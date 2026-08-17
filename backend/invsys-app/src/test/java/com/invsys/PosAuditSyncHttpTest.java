package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.AuditLog;
import com.invsys.domain.subscription.AppModule;
import com.invsys.repository.AuditLogRepository;
import com.invsys.service.TenantSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PosAuditSyncHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantSubscriptionService tenantSubscriptionService;
    @Autowired AuditLogRepository auditLogRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void auditSync_writesImmutableLossPreventionRowsAndDedupes() throws Exception {
        TokenResponse owner = signup("pos-audit");
        TenantContext.setTenantId(owner.tenantId());
        TenantContext.setUserId(owner.userId());
        authService.setTerminalPin(owner.userId(), "1234");
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/pos/manager-overrides")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(owner.tenantId().toString()))
                .andExpect(jsonPath("$.managers[0].managerId").value(owner.userId().toString()))
                .andExpect(jsonPath("$.managers[0].pinHash").isNotEmpty());

        UUID eventId = UUID.randomUUID();
        String payload = """
                [{"id":"%s","timestamp":1700000000000,"cashierId":"%s","eventType":"TX_VOID",
                  "orderId":"%s","valueVoided":42.50,"managerOverrideId":"%s"}]
                """.formatted(eventId, owner.userId(), UUID.randomUUID(), owner.userId());

        mockMvc.perform(post("/api/v1/pos/audit-sync")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.duplicates").value(0));

        mockMvc.perform(post("/api/v1/pos/audit-sync")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.accepted").value(0));

        TenantContext.setTenantId(owner.tenantId());
        List<AuditLog> logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(owner.tenantId());
        assertThat(logs).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("POS_TX_VOID");
            assertThat(entry.getEntityType()).isEqualTo("POS_EXCEPTION");
            assertThat(entry.getEntityId()).isEqualTo(eventId);
            assertThat(entry.getDiff()).containsEntry("valueVoided", "42.50");
            assertThat(entry.getDiff()).containsEntry("source", "POS_OFFLINE_AUDIT");
        });
    }

    @Test
    void auditSync_rejectsUnknownTypeAndLockedModule() throws Exception {
        TokenResponse owner = signup("pos-audit-lock");

        mockMvc.perform(post("/api/v1/pos/audit-sync")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","timestamp":1,"cashierId":"%s","eventType":"TIP_OUT",
                                  "orderId":"%s","valueVoided":1.00}]
                                """.formatted(UUID.randomUUID(), owner.userId(), UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejected[0].reason").exists());

        tenantSubscriptionService.replaceEnabledModules(owner.tenantId(), List.of(AppModule.CORE));

        mockMvc.perform(post("/api/v1/pos/audit-sync")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"id":"%s","timestamp":1,"cashierId":"%s","eventType":"LINE_VOID",
                                  "orderId":"%s","valueVoided":1.00}]
                                """.formatted(UUID.randomUUID(), owner.userId(), UUID.randomUUID())))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("MODULE_LOCKED"));
    }

    @Test
    void auditSync_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/pos/audit-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    private TokenResponse signup(String prefix) {
        String slug = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return authService.signup(new SignupRequest(
                "POS Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
    }
}
