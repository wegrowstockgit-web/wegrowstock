package com.invsys.admin;

import com.invsys.admin.security.AdminCookieService;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import com.invsys.service.TenantSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ControlPlanePackagingHttpTest extends AbstractAdminIntegrationTest {

    private static final String DEMO_BCRYPT =
            "$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu";

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantSubscriptionService tenantSubscriptionService;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void packagingEndpointsRequireSuperAdmin_andRoundTripBundle() throws Exception {
        mockMvc.perform(get("/api/v1/control-plane/packaging/tiers"))
                .andExpect(status().isUnauthorized());

        String slug = "pkg-" + UUID.randomUUID().toString().substring(0, 8);
        authService.signup(new SignupRequest(
                "Packaging Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String adminEmail = "platform@" + slug + ".test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);

        mockMvc.perform(get("/api/v1/control-plane/packaging/tiers")
                        .cookie(accessCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tierCode=='BASIC')].defaultModules[0]").value("CORE"))
                .andExpect(jsonPath("$[?(@.tierCode=='INTERMEDIATE')].defaultModules").isArray())
                .andExpect(jsonPath("$[?(@.tierCode=='ENTERPRISE')].displayName").value("Enterprise"));

        mockMvc.perform(put("/api/v1/control-plane/packaging/tiers/BASIC")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultModules":["CORE","SHOPIFY"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tierCode").value("BASIC"))
                .andExpect(jsonPath("$.defaultModules").value(org.hamcrest.Matchers.hasItems("CORE", "SHOPIFY")));

        assertThat(tenantSubscriptionService.getDefaultModulesForTier(CommercialTier.BASIC))
                .containsExactlyInAnyOrder(AppModule.CORE, AppModule.SHOPIFY);

        mockMvc.perform(put("/api/v1/control-plane/packaging/tiers/BASIC")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultModules":["CORE"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultModules").value(org.hamcrest.Matchers.contains("CORE")));
    }

    @Test
    void liveBundleIsUsedWhenAssigningTier() throws Exception {
        String slug = "live-" + UUID.randomUUID().toString().substring(0, 8);
        var owner = authService.signup(new SignupRequest(
                "Live Bundle Co", slug, "owner@" + slug + ".test", "password123", "Owner"));
        String adminEmail = "platform@" + slug + ".test";
        insertPlatformAdmin(adminEmail);
        var accessCookie = loginAdmin(adminEmail);

        mockMvc.perform(put("/api/v1/control-plane/packaging/tiers/BASIC")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(accessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultModules":["CORE","DOCUMENTS"]}
                                """))
                .andExpect(status().isOk());

        var applied = tenantSubscriptionService.replaceTier(owner.tenantId(), CommercialTier.BASIC);
        assertThat(applied.enabledModules()).contains(AppModule.CORE, AppModule.DOCUMENTS);

        tenantSubscriptionService.replaceTierDefinition("BASIC", List.of(AppModule.CORE));
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
