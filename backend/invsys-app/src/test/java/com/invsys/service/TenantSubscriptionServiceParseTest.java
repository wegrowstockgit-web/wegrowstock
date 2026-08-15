package com.invsys.service;

import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TenantSubscriptionServiceParseTest {

    private final TenantSubscriptionService service = new TenantSubscriptionService(
            null, null, null, null, null);

    @Test
    void parseModules_readsJsonArray() {
        assertThat(TenantSubscriptionService.parseModules("[\"CORE\",\"FINTECH\"]"))
                .containsExactly(AppModule.CORE, AppModule.FINTECH);
    }

    @Test
    void parseModules_defaultsToCore() {
        assertThat(TenantSubscriptionService.parseModules(null)).containsExactly(AppModule.CORE);
        assertThat(TenantSubscriptionService.parseModules("[]")).containsExactly(AppModule.CORE);
    }

    @Test
    void commercialTier_fromString() {
        assertThat(CommercialTier.fromString("enterprise")).isEqualTo(CommercialTier.ENTERPRISE);
        assertThat(CommercialTier.fromString(null)).isEqualTo(CommercialTier.BASIC);
    }

    @Test
    void appModule_fromString() {
        assertThat(AppModule.fromString("b2b_showroom")).isEqualTo(AppModule.B2B_SHOWROOM);
        assertThat(AppModule.fromString("ai_copilot")).isEqualTo(AppModule.AI_COPILOT);
        assertThat(AppModule.values()).hasSize(12);
    }

    @Test
    void parseModules_preservesDeclarationOrder() {
        List<AppModule> mods = TenantSubscriptionService.parseModules("[\"MRP\",\"CORE\",\"SHOPIFY\"]");
        assertThat(mods).containsExactly(AppModule.CORE, AppModule.SHOPIFY, AppModule.MRP);
    }

    @Test
    void getDefaultModulesForTier_basicIsCoreOnly() {
        assertThat(service.getDefaultModulesForTier(CommercialTier.BASIC))
                .containsExactly(AppModule.CORE);
    }

    @Test
    void getDefaultModulesForTier_intermediateBundle() {
        Set<AppModule> mods = service.getDefaultModulesForTier(CommercialTier.INTERMEDIATE);
        assertThat(mods).containsExactlyInAnyOrder(
                AppModule.CORE,
                AppModule.SHOPIFY,
                AppModule.ACCOUNTING,
                AppModule.ADVANCED_FULFILLMENT,
                AppModule.MANUFACTURING,
                AppModule.DOCUMENTS,
                AppModule.MRP);
        assertThat(mods).doesNotContain(AppModule.FINTECH, AppModule.AI_COPILOT);
    }

    @Test
    void getDefaultModulesForTier_enterpriseIsAll() {
        assertThat(service.getDefaultModulesForTier(CommercialTier.ENTERPRISE))
                .isEqualTo(EnumSet.allOf(AppModule.class));
    }
}
