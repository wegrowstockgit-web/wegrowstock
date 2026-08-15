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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ControlPlaneTenantHttpTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void controlPlaneRequiresPlatformAdmin_andListsTenants() throws Exception {
        String slug = "cp-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Control Plane Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(get("/api/v1/control-plane/tenants"))
                .andExpect(status().isUnauthorized());

        String adminEmail = "platform@" + slug + ".test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);

        mockMvc.perform(get("/api/v1/control-plane/tenants")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tenantId=='" + owner.tenantId() + "')].slug").value(slug));

        mockMvc.perform(patch("/api/v1/control-plane/tenants/" + owner.tenantId() + "/modules")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabledModules":["CORE","B2B_SHOWROOM"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabledModules").isArray())
                .andExpect(jsonPath("$.enabledModules[?(@=='FINTECH')]").doesNotExist());
    }

    @Test
    void patchTierAppliesBundle() throws Exception {
        String slug = "tier-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Tier Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String adminEmail = "platform@" + slug + ".test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);

        mockMvc.perform(patch("/api/v1/control-plane/tenants/" + owner.tenantId() + "/tier")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tier":"INTERMEDIATE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.enabledModules").value(org.hamcrest.Matchers.hasItems(
                        "CORE", "SHOPIFY", "ACCOUNTING", "ADVANCED_FULFILLMENT",
                        "MANUFACTURING", "DOCUMENTS", "MRP")))
                .andExpect(jsonPath("$.enabledModules[?(@=='FINTECH')]").doesNotExist());
    }

    @Test
    void wmsTokenCannotAccessControlPlane() throws Exception {
        String slug = "wms-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "WMS Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(get("/api/v1/control-plane/tenants")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isUnauthorized());
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
