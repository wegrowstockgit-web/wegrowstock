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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ControlPlaneOpsHttpTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void phase4And5ControlPlaneOps() throws Exception {
        String slug = "ops-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Ops Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        // Platform admin (owner@demo.test pattern — unique per run)
        String adminEmail = "owner@demo.test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);

        mockMvc.perform(get("/api/v1/control-plane/tenants")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tenantId=='" + owner.tenantId() + "')].slug").value(slug));

        mockMvc.perform(post("/api/v1/control-plane/tenants/" + owner.tenantId() + "/impersonate")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(notNullValue()))
                .andExpect(jsonPath("$.handoffCode").value(notNullValue()))
                .andExpect(jsonPath("$.handoffToken").value(notNullValue()))
                .andExpect(jsonPath("$.redirectUrl").value(notNullValue()))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.email").value(notNullValue()))
                .andExpect(jsonPath("$.loginUrl").value(notNullValue()));

        mockMvc.perform(patch("/api/v1/control-plane/tenants/" + owner.tenantId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // Re-activate for subsequent ops that need an ACTIVE tenant
        mockMvc.perform(patch("/api/v1/control-plane/tenants/" + owner.tenantId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/v1/control-plane/billing/overview")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedMrr").value(notNullValue()))
                .andExpect(jsonPath("$.tenants").isArray());

        mockMvc.perform(post("/api/v1/control-plane/integrations/tenants/" + owner.tenantId() + "/kill-switch")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paused":true,"reason":"ops-test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));

        mockMvc.perform(get("/api/v1/control-plane/audit-logs")
                        .param("limit", "20")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        MockMultipartFile md = new MockMultipartFile(
                "file",
                "runbook.md",
                "text/markdown",
                "# Ops Runbook\n\nRestart the worker and check health.".getBytes());
        mockMvc.perform(multipart("/api/v1/control-plane/knowledge/ingest")
                        .file(md)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(notNullValue()))
                .andExpect(jsonPath("$.chunkCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(put("/api/v1/control-plane/shards/" + owner.tenantId())
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shardKey":"shard-a","region":"us-west-2","notes":"ops-test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shardKey").value("shard-a"));

        mockMvc.perform(get("/api/v1/control-plane/shards/" + owner.tenantId())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shardKey").value("shard-a"));

        mockMvc.perform(get("/api/v1/control-plane/queues/dead-letters")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(put("/api/v1/control-plane/telemetry/tenants/" + owner.tenantId() + "/rate-limit")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"capacityMultiplier":2.5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityMultiplier").value(2.5));

        mockMvc.perform(post("/api/v1/control-plane/compliance/broadcasts")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"TAX","title":"VAT update","payload":{"scheme":"VAT"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("VAT update"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/control-plane/tenants/" + owner.tenantId() + "/clone-sandbox")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sandboxTenantId").value(notNullValue()))
                .andExpect(jsonPath("$.apiKey").value(notNullValue()))
                .andExpect(jsonPath("$.sandboxSlug").value(org.hamcrest.Matchers.startsWith("uat-")));

        // Commercial + health reports (AdminReportingService)
        mockMvc.perform(get("/api/v1/control-plane/reports/commercial").cookie(accessCookie))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/control-plane/reports/health").cookie(accessCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/control-plane/integrations/traffic").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/control-plane/knowledge").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        UUID docId = new JdbcTemplate(bootstrapDataSource).queryForObject(
                "SELECT id FROM platform_knowledge_documents ORDER BY created_at DESC LIMIT 1", UUID.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/control-plane/knowledge/" + docId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/control-plane/shards").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/control-plane/telemetry/tenants").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/control-plane/compliance/broadcasts").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        UUID broadcastId = new JdbcTemplate(bootstrapDataSource).queryForObject(
                "SELECT id FROM platform_compliance_broadcasts ORDER BY created_at DESC LIMIT 1", UUID.class);
        mockMvc.perform(post("/api/v1/control-plane/compliance/broadcasts/" + broadcastId + "/activate")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // DLQ inspect + retry
        UUID eventId = UUID.randomUUID();
        new JdbcTemplate(bootstrapDataSource).update("""
                INSERT INTO outbox_events (
                    id, tenant_id, aggregate_type, aggregate_id, event_type, payload,
                    status, retry_count, last_error, created_at, updated_at
                ) VALUES (?, ?, 'TEST', ?, 'TEST_EVENT', '{}'::jsonb, 'FAILED', 3, 'boom', NOW(), NOW())
                """, eventId, owner.tenantId(), UUID.randomUUID());

        mockMvc.perform(get("/api/v1/control-plane/queues/dead-letters").cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/v1/control-plane/queues/dead-letters/" + eventId).cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.lastError").value("boom"));

        mockMvc.perform(post("/api/v1/control-plane/queues/dead-letters/" + eventId + "/retry")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(patch("/api/v1/control-plane/tenants/" + owner.tenantId() + "/modules")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledModules":["CORE","B2B_SHOWROOM"]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/control-plane/auth/csrf"))
                .andExpect(status().isNoContent());
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
