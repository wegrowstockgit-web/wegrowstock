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
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminCsrfSecurityTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void tenantModulePatchRequiresValidCsrfHeader() throws Exception {
        String slug = "csrf-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Csrf Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String adminEmail = "platform@" + slug + ".test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);

        String path = "/api/v1/control-plane/tenants/" + owner.tenantId() + "/modules";
        String body = """
                {"enabledModules":["CORE","B2B_SHOWROOM"]}
                """;

        mockMvc.perform(patch(path)
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(path)
                        .cookie(accessCookie)
                        .header("X-XSRF-TOKEN", "not-a-real-csrf-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/control-plane/auth/csrf").cookie(accessCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(patch(path)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
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
