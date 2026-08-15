package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import com.invsys.service.TenantSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ControlPlaneTenantEntitlementHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired TenantSubscriptionService tenantSubscriptionService;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("bootstrapDataSource")
    DataSource bootstrapDataSource;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void moduleGateReturns402WhenFintechDisabled() throws Exception {
        String slug = "cp-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Control Plane Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        tenantSubscriptionService.replaceEnabledModules(
                owner.tenantId(), List.of(AppModule.CORE, AppModule.B2B_SHOWROOM));

        assertThat(tenantSubscriptionService.getEnabledModules(owner.tenantId()))
                .containsExactly(AppModule.CORE, AppModule.B2B_SHOWROOM);

        mockMvc.perform(get("/api/v1/fintech/dashboard")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("MODULE_LOCKED"));

        tenantSubscriptionService.replaceEnabledModules(
                owner.tenantId(), List.of(AppModule.CORE, AppModule.FINTECH));

        mockMvc.perform(get("/api/v1/fintech/dashboard")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void meExposesEnabledModulesAndCacheEvictsOnPatch() throws Exception {
        String slug = "mod-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Modules Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        List<AppModule> before = tenantSubscriptionService.getEnabledModules(owner.tenantId());
        assertThat(before).contains(AppModule.CORE, AppModule.FINTECH);

        tenantSubscriptionService.replaceEnabledModules(owner.tenantId(), List.of(AppModule.CORE));

        assertThat(tenantSubscriptionService.getEnabledModules(owner.tenantId()))
                .containsExactly(AppModule.CORE);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuperAdmin").value(false))
                .andExpect(jsonPath("$.enabledModules[0]").value("CORE"));
    }

    @Test
    void patchTierAppliesBundleAndPreservesCustomAddOns() {
        String slug = "tier-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Tier Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        var intermediate = tenantSubscriptionService.replaceTier(owner.tenantId(), CommercialTier.INTERMEDIATE);
        assertThat(intermediate.tier()).isEqualTo(CommercialTier.INTERMEDIATE);
        assertThat(intermediate.enabledModules()).contains(
                AppModule.CORE, AppModule.SHOPIFY, AppModule.ACCOUNTING,
                AppModule.ADVANCED_FULFILLMENT, AppModule.MANUFACTURING,
                AppModule.DOCUMENTS, AppModule.MRP);
        assertThat(intermediate.enabledModules()).doesNotContain(AppModule.FINTECH);

        tenantSubscriptionService.replaceEnabledModules(owner.tenantId(), List.of(
                AppModule.CORE, AppModule.SHOPIFY, AppModule.ACCOUNTING,
                AppModule.ADVANCED_FULFILLMENT, AppModule.MANUFACTURING,
                AppModule.DOCUMENTS, AppModule.MRP, AppModule.FINTECH));

        var basic = tenantSubscriptionService.replaceTier(owner.tenantId(), CommercialTier.BASIC);
        assertThat(basic.tier()).isEqualTo(CommercialTier.BASIC);
        assertThat(basic.enabledModules()).containsExactlyInAnyOrder(AppModule.CORE, AppModule.FINTECH);
        assertThat(tenantSubscriptionService.getEnabledModules(owner.tenantId()))
                .containsExactlyInAnyOrder(AppModule.CORE, AppModule.FINTECH);

        var enterprise = tenantSubscriptionService.replaceTier(owner.tenantId(), CommercialTier.ENTERPRISE);
        assertThat(enterprise.tier()).isEqualTo(CommercialTier.ENTERPRISE);
        assertThat(enterprise.enabledModules()).hasSize(AppModule.values().length);
    }
}
