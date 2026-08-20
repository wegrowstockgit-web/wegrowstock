package com.invsys.admin;

import com.invsys.admin.security.AdminCookieService;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ControlPlaneGovernanceHttpTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void throttleFlagsImpersonationAuditAndPurge() throws Exception {
        String slug = "gov-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Gov Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        String adminEmail = "owner@demo.test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);
        JdbcTemplate jdbc = new JdbcTemplate(bootstrapDataSource);

        mockMvc.perform(patch("/api/v1/control-plane/telemetry/tenants/" + tenantId + "/throttle")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customRateLimit":25,"isThrottled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isThrottled").value(true))
                .andExpect(jsonPath("$.customRateLimit").value(25));

        Boolean throttled = jdbc.queryForObject(
                "SELECT is_throttled FROM tenants WHERE id = ?", Boolean.class, tenantId);
        Integer rps = jdbc.queryForObject(
                "SELECT custom_rate_limit FROM tenants WHERE id = ?", Integer.class, tenantId);
        assertThat(throttled).isTrue();
        assertThat(rps).isEqualTo(25);

        String flagKey = "beta-dock-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/v1/control-plane/flags")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flagKey":"%s","description":"Dock beta","isGlobal":false}
                                """.formatted(flagKey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagKey").value(flagKey))
                .andExpect(jsonPath("$.isGlobal").value(false));

        UUID flagId = jdbc.queryForObject(
                "SELECT id FROM feature_flags WHERE flag_key = ?", UUID.class, flagKey);

        mockMvc.perform(put("/api/v1/control-plane/flags/" + flagId + "/tenants")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isGlobal":false,"overrides":[{"tenantId":"%s","enabled":true}]}
                                """.formatted(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants[0].tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.tenants[0].enabled").value(true));

        mockMvc.perform(get("/api/v1/control-plane/flags").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].flagKey", hasItem(flagKey)));

        mockMvc.perform(post("/api/v1/control-plane/tenants/" + tenantId + "/impersonate")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/control-plane/audit-logs")
                        .param("impersonationOnly", "true")
                        .param("limit", "20")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("TENANT_IMPERSONATE"))
                .andExpect(jsonPath("$[0].actorType").value("PLATFORM_ADMIN_IMPERSONATION"));

        mockMvc.perform(post("/api/v1/control-plane/tenants/" + tenantId + "/purge")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PURGED"));

        String status = jdbc.queryForObject("SELECT status FROM tenants WHERE id = ?", String.class, tenantId);
        assertThat(status).isEqualTo("PURGED");
        String email = jdbc.queryForObject(
                "SELECT email FROM users WHERE tenant_id = ? LIMIT 1", String.class, tenantId);
        assertThat(email).startsWith("purged-");
        Integer purgeEvents = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE tenant_id = ? AND event_type = 'TENANT_S3_PURGE'",
                Integer.class, tenantId);
        assertThat(purgeEvents).isGreaterThanOrEqualTo(1);
        Integer refreshLeft = jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE tenant_id = ?", Integer.class, tenantId);
        assertThat(refreshLeft).isZero();
    }

    private jakarta.servlet.http.Cookie loginAdmin(String email) throws Exception {
        var login = mockMvc.perform(post("/api/v1/control-plane/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return login.getResponse().getCookie(AdminCookieService.ACCESS_COOKIE);
    }

    private void insertPlatformAdmin(String email) {
        new JdbcTemplate(bootstrapDataSource).update(
                """
                INSERT INTO platform_admins (id, email, password_hash, active)
                VALUES (?, ?, ?, TRUE)
                ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, active = TRUE
                """,
                UUID.randomUUID(), email, DEMO_BCRYPT);
    }
}
