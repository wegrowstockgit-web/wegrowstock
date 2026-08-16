package com.invsys.admin;

import com.invsys.admin.security.AdminLoginAttemptLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminLoginBruteForceTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired AdminLoginAttemptLimiter loginAttemptLimiter;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @AfterEach
    void resetLimiter() {
        loginAttemptLimiter.resetLocal();
    }

    @Test
    void sixthFailedLoginReturns429() throws Exception {
        String email = "brute@" + UUID.randomUUID().toString().substring(0, 8) + ".test";
        insertPlatformAdmin(email);
        String attackerIp = "203.0.113.50";
        String payload = """
                {"email":"%s","password":"wrong-password"}
                """.formatted(email);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/control-plane/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(attackerIp);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/v1/control-plane/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(attackerIp);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(AdminLoginAttemptLimiter.RATE_LIMIT_CODE));
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
