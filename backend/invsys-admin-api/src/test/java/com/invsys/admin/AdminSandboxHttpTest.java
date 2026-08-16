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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminSandboxHttpTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void cloneSandbox_provisionsThenReusesAndRejectsSuspended() throws Exception {
        String slug = "sbx-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Sandbox Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        insertPlatformAdmin("owner@demo.test");
        var accessCookie = loginAdmin("owner@demo.test");

        Integer provisionPolicy = new JdbcTemplate(bootstrapDataSource).queryForObject(
                """
                SELECT COUNT(*) FROM pg_policy
                WHERE polrelid = 'tenants'::regclass
                  AND polname = 'bootstrap_tenant_provision'
                """,
                Integer.class);
        assertThat(provisionPolicy).isEqualTo(1);

        String firstBody = mockMvc.perform(post("/api/v1/control-plane/tenants/" + owner.tenantId() + "/clone-sandbox")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceTenantId").value(owner.tenantId().toString()))
                .andExpect(jsonPath("$.sandboxTenantId").value(notNullValue()))
                .andExpect(jsonPath("$.sandboxSlug").value(startsWith("uat-")))
                .andExpect(jsonPath("$.apiKey").value(startsWith("sk_uat_")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sandboxId = firstBody.replaceAll(".*\"sandboxTenantId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/v1/control-plane/tenants/" + owner.tenantId() + "/clone-sandbox")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sandboxTenantId").value(sandboxId));

        mockMvc.perform(patch("/api/v1/control-plane/tenants/" + owner.tenantId() + "/status")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/control-plane/tenants/" + owner.tenantId() + "/clone-sandbox")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("TENANT_SUSPENDED"));
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
