package com.invsys.admin;

import com.invsys.admin.security.AdminCookieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminPageKnowledgeHttpTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void superAdminCanCrudPageHelp() throws Exception {
        String adminEmail = "pk-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);

        mockMvc.perform(get("/api/v1/admin/page-knowledge")
                        .param("category", "Inbound")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$[0].category").value("Inbound"));

        String route = "/e2e-help-" + UUID.randomUUID().toString().substring(0, 8);
        var created = mockMvc.perform(post("/api/v1/admin/page-knowledge")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "routePattern": "%s",
                                  "category": "Core",
                                  "title": "E2E Help",
                                  "summary": "Beginner help for weGrowStock testers.",
                                  "rolePrivileges": "Owners only.",
                                  "keyActions": ["Open the page", "Read the tip"],
                                  "commonMistakes": [
                                    {
                                      "mistake": "Fat-fingered a quantity",
                                      "solution": "Post a reversing ledger entry. Never delete history.",
                                      "requiredRole": "WAREHOUSE_MANAGER"
                                    }
                                  ],
                                  "proTip": "Search before you create."
                                }
                                """.formatted(route)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("E2E Help"))
                .andExpect(jsonPath("$.keyActions.length()").value(2))
                .andReturn();

        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(put("/api/v1/admin/page-knowledge/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "routePattern": "%s",
                                  "category": "Core",
                                  "title": "E2E Help Updated",
                                  "summary": "Updated summary.",
                                  "rolePrivileges": "Owners and Admins.",
                                  "keyActions": ["Updated action"],
                                  "commonMistakes": [
                                    {
                                      "mistake": "Duplicate entry",
                                      "solution": "Cancel the twin. Do not erase the first.",
                                      "requiredRole": "ADMIN"
                                    }
                                  ],
                                  "proTip": "Updated tip"
                                }
                                """.formatted(route)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("E2E Help Updated"));

        mockMvc.perform(get("/api/v1/control-plane/page-knowledge")
                        .param("search", "E2E Help Updated")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("E2E Help Updated"));

        mockMvc.perform(delete("/api/v1/admin/page-knowledge/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/page-knowledge")
                        .param("search", route)
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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
