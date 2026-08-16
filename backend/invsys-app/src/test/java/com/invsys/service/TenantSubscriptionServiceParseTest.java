package com.invsys.service;

import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import com.invsys.domain.subscription.PlatformTierDefinition;
import com.invsys.repository.PlatformTierDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class TenantSubscriptionServiceParseTest {

    @Mock PlatformTierDefinitionRepository tierDefinitionRepository;

    private TenantSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new TenantSubscriptionService(
                null, null, tierDefinitionRepository, null, null, null, null);
        lenient().when(tierDefinitionRepository.findById("BASIC")).thenReturn(Optional.of(definition(
                "BASIC", List.of("CORE"))));
        lenient().when(tierDefinitionRepository.findById("INTERMEDIATE")).thenReturn(Optional.of(definition(
                "INTERMEDIATE",
                List.of("CORE", "SHOPIFY", "ACCOUNTING", "ADVANCED_FULFILLMENT",
                        "MANUFACTURING", "DOCUMENTS", "MRP"))));
        lenient().when(tierDefinitionRepository.findById("ENTERPRISE")).thenReturn(Optional.of(definition(
                "ENTERPRISE",
                List.of("CORE", "SHOPIFY", "ACCOUNTING", "ADVANCED_FULFILLMENT",
                        "MANUFACTURING", "DOCUMENTS", "MRP", "B2B_SHOWROOM",
                        "FINTECH", "MESH_NETWORK", "RTLS_TELEMETRY", "AI_COPILOT",
                        "RETAIL_POS"))));
    }

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
        assertThat(AppModule.fromString("retail_pos")).isEqualTo(AppModule.RETAIL_POS);
        assertThat(AppModule.values()).hasSize(13);
    }

    @Test
    void parseModules_preservesDeclarationOrder() {
        List<AppModule> mods = TenantSubscriptionService.parseModules("[\"MRP\",\"CORE\",\"SHOPIFY\"]");
        assertThat(mods).containsExactly(AppModule.CORE, AppModule.SHOPIFY, AppModule.MRP);
    }

    @Test
    void getDefaultModulesForTier_readsDatabaseBundle() {
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
        assertThat(mods).doesNotContain(AppModule.FINTECH, AppModule.AI_COPILOT, AppModule.RETAIL_POS);
    }

    @Test
    void getDefaultModulesForTier_enterpriseIsAll() {
        assertThat(service.getDefaultModulesForTier(CommercialTier.ENTERPRISE))
                .isEqualTo(EnumSet.allOf(AppModule.class));
    }

    private static PlatformTierDefinition definition(String code, List<String> modules) {
        PlatformTierDefinition def = new PlatformTierDefinition();
        def.setTierCode(code);
        def.setDisplayName(code);
        def.setDefaultModules(modules);
        return def;
    }
}
