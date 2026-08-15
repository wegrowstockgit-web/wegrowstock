package com.invsys.admin;

import com.invsys.admin.security.AdminCookieService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminAuthHttpTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void loginRequiresPlatformAdmin_andIssuesAdminCookies() throws Exception {
        String email = "owner@" + UUID.randomUUID().toString().substring(0, 8) + ".test";

        mockMvc.perform(post("/api/v1/control-plane/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        insertPlatformAdmin(email);

        mockMvc.perform(post("/api/v1/control-plane/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.superAdmin").value(true))
                .andExpect(cookie().exists(AdminCookieService.ACCESS_COOKIE))
                .andExpect(cookie().exists(AdminCookieService.REFRESH_COOKIE));
    }

    @Test
    void meRequiresAdminSession() throws Exception {
        String email = "me@" + UUID.randomUUID().toString().substring(0, 8) + ".test";
        insertPlatformAdmin(email);

        var login = mockMvc.perform(post("/api/v1/control-plane/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        var accessCookie = login.getResponse().getCookie(AdminCookieService.ACCESS_COOKIE);
        mockMvc.perform(get("/api/v1/control-plane/auth/me")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.superAdmin").value(true));

        mockMvc.perform(post("/api/v1/control-plane/auth/logout")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isNoContent());
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
