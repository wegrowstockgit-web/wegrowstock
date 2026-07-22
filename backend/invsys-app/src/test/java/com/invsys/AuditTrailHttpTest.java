package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.domain.AuditLog;
import com.invsys.repository.AuditLogRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditTrailHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void settingsPatchWritesTriggerAndAopInviteWritesApplicationAudit() throws Exception {
        String slug = "aud-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Audit Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        mockMvc.perform(patch("/api/v1/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Request-Id", "audit-corr-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"picking_wave_max_lines":41}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.picking_wave_max_lines").value(41));

        mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Request-Id", "audit-invite-9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@%s.test","role":"VIEWER"}
                                """.formatted(slug)))
                .andExpect(status().isOk());

        TenantContext.setTenantId(tenantId);
        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(logs).extracting(AuditLog::getAction)
                .contains("TG_UPDATE", "INVITE_USER", "USER_INVITE");

        AuditLog triggerRow = logs.stream()
                .filter(a -> "TG_UPDATE".equals(a.getAction()) && "TENANT_SETTINGS".equals(a.getEntityType()))
                .findFirst()
                .orElseThrow();
        assertThat(triggerRow.getDiff()).containsEntry("source", "postgres_trigger");
        assertThat(String.valueOf(triggerRow.getDiff().get("table"))).isEqualTo("tenant_settings");

        AuditLog aopRow = logs.stream()
                .filter(a -> "INVITE_USER".equals(a.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(aopRow.getActorUserId()).isEqualTo(owner.userId());
        assertThat(aopRow.getDiff()).containsEntry("source", "spring_aop");
        assertThat(aopRow.getDiff().get("requestId")).isEqualTo("audit-invite-9");
    }

    @Test
    void auditLogIsImmutableForAppUser() {
        String slug = "imm-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Imm Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        TenantContext.setTenantId(owner.tenantId());
        TenantContext.setUserId(owner.userId());
        // Force a settings write so at least one audit row exists via trigger.
        jdbcTemplate.update("""
                UPDATE tenant_settings
                   SET settings = settings || '{"imm":true}'::jsonb
                 WHERE tenant_id = ?
                """, owner.tenantId());

        UUID auditId = jdbcTemplate.queryForObject("""
                SELECT id FROM audit_log
                 WHERE tenant_id = ?
                 ORDER BY created_at DESC
                 LIMIT 1
                """, UUID.class, owner.tenantId());
        assertThat(auditId).isNotNull();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_log SET action = 'TAMPERED' WHERE id = ?", auditId))
                .hasRootCauseMessage("ERROR: permission denied for table audit_log");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_log WHERE id = ?", auditId))
                .hasRootCauseMessage("ERROR: permission denied for table audit_log");
    }
}
