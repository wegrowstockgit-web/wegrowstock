package com.invsys.pos;

import com.invsys.core.common.ApiException;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.ProductMedia;
import com.invsys.domain.Tenant;
import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.pos.dto.PosCatalogItem;
import com.invsys.pos.dto.PosSessionResponse;
import com.invsys.repository.TenantRepository;
import com.invsys.service.SettingsService;
import com.invsys.service.TenantSubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosSessionServiceTest {

    @Mock AuthService authService;
    @Mock SettingsService settingsService;
    @Mock TenantSubscriptionService tenantSubscriptionService;
    @Mock TenantRepository tenantRepository;
    @Mock com.invsys.service.CurrencyService currencyService;
    @Mock com.invsys.modules.catalog.repository.ProductVariantRepository variantRepository;
    @Mock com.invsys.modules.catalog.repository.ProductRepository productRepository;
    @Mock com.invsys.modules.catalog.repository.ProductMediaRepository productMediaRepository;
    @Mock com.invsys.repository.VariantBarcodeRepository variantBarcodeRepository;

    @InjectMocks PosSessionService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void currentSession_appliesOrganizationLanguageAndPlaceDisplayCurrencyWhenPosEnabled() {
        UUID tenantId = UUID.randomUUID();
        when(authService.currentUser()).thenReturn(me(tenantId, "fr", "ENTERPRISE"));
        when(tenantSubscriptionService.isModuleEnabled(tenantId, AppModule.RETAIL_POS)).thenReturn(true);
        when(settingsService.getSettings()).thenReturn(Map.of(
                "locale_language", "es",
                "currency", "EUR",
                "timezone", "Europe/Paris"));
        when(currencyService.quoteOrOne("EUR", "MXN")).thenReturn(new BigDecimal("18.5000"));
        Tenant tenant = new Tenant();
        tenant.setName("Demo Corp");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        PosSessionResponse session = service.currentSession("en-US", "America/Mexico_City", "en", "MXN");

        assertThat(session.posEnabled()).isTrue();
        assertThat(session.language()).isEqualTo("es");
        assertThat(session.languageSource()).isEqualTo("ORGANIZATION");
        assertThat(session.currency()).isEqualTo("MXN");
        assertThat(session.currencySource()).isEqualTo("PLACE");
        assertThat(session.tenantBaseCurrency()).isEqualTo("EUR");
        assertThat(session.liveExchangeRate()).isEqualByComparingTo("18.5000");
        assertThat(session.placeCurrency()).isEqualTo("MXN");
        assertThat(session.taxRegionHint()).isEqualTo("MX");
        assertThat(session.companyName()).isEqualTo("Demo Corp");
        assertThat(session.module()).isEqualTo("RETAIL_POS");
        assertThat(session.tier()).isEqualTo("ENTERPRISE");
        assertThat(session.cashierId()).isNotNull();
        assertThat(session.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void currentSession_ignoresWmsLocaleWhenModuleLocked() {
        UUID tenantId = UUID.randomUUID();
        when(authService.currentUser()).thenReturn(me(tenantId, "fr", "BASIC"));
        when(tenantSubscriptionService.isModuleEnabled(tenantId, AppModule.RETAIL_POS)).thenReturn(false);
        when(settingsService.getSettings()).thenReturn(Map.of(
                "locale_language", "es",
                "currency", "EUR"));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(currencyService.quoteOrOne("EUR", "MXN")).thenReturn(BigDecimal.ONE);

        PosSessionResponse session = service.currentSession("es-MX", null, null, null);

        assertThat(session.posEnabled()).isFalse();
        assertThat(session.language()).isEqualTo("es");
        assertThat(session.languageSource()).isEqualTo("PLACE");
        assertThat(session.currency()).isEqualTo("MXN");
        assertThat(session.currencySource()).isEqualTo("PLACE");
        assertThat(session.companyName()).isEmpty();
        assertThat(session.tier()).isEqualTo("BASIC");
    }

    @Test
    void currentSession_fallsBackToUserLanguageAndPlaceCurrency() {
        UUID tenantId = UUID.randomUUID();
        when(authService.currentUser()).thenReturn(me(tenantId, "fr", null));
        when(tenantSubscriptionService.isModuleEnabled(tenantId, AppModule.RETAIL_POS)).thenReturn(true);
        when(tenantSubscriptionService.getCommercialTier(tenantId)).thenReturn(CommercialTier.ENTERPRISE);
        when(settingsService.getSettings()).thenReturn(Map.of());
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        when(currencyService.quoteOrOne("USD", "GBP")).thenReturn(new BigDecimal("0.7800"));

        PosSessionResponse session = service.currentSession("en-GB", "Europe/London", null, null);

        assertThat(session.language()).isEqualTo("fr");
        assertThat(session.languageSource()).isEqualTo("USER");
        assertThat(session.currency()).isEqualTo("GBP");
        assertThat(session.currencySource()).isEqualTo("PLACE");
        assertThat(session.tenantBaseCurrency()).isEqualTo("USD");
        assertThat(session.liveExchangeRate()).isEqualByComparingTo("0.7800");
        assertThat(session.tier()).isEqualTo("ENTERPRISE");
        assertThat(session.timezone()).isEqualTo("Europe/London");
    }

    @Test
    void syncCatalog_returnsActivePricedVariantsWithUpc() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        ProductVariant sellable = new ProductVariant();
        sellable.setId(variantId);
        sellable.setTenantId(tenantId);
        sellable.setProductId(productId);
        sellable.setSku("POS-1");
        sellable.setBarcode("7700222200099");
        sellable.setPrice(new BigDecimal("4.50"));
        sellable.setLifecycleStatus("ACTIVE");

        ProductVariant noBarcode = new ProductVariant();
        noBarcode.setId(UUID.randomUUID());
        noBarcode.setTenantId(tenantId);
        noBarcode.setProductId(productId);
        noBarcode.setSku("SKIP");
        noBarcode.setPrice(BigDecimal.TEN);
        noBarcode.setLifecycleStatus("ACTIVE");

        Product product = new Product();
        product.setId(productId);
        product.setName("POS Widget");

        ProductMedia media = new ProductMedia();
        media.setVariantId(variantId);
        media.setUrl("/api/v1/media/abc/content");
        media.setPrimary(true);

        when(variantRepository.findByTenantIdAndLifecycleStatus(tenantId, "ACTIVE"))
                .thenReturn(List.of(sellable, noBarcode));
        when(productRepository.findAllById(List.of(productId))).thenReturn(List.of(product));
        when(productMediaRepository.findByTenantIdAndVariantIdInAndPrimaryTrue(tenantId, List.of(variantId)))
                .thenReturn(List.of(media));

        List<PosCatalogItem> items = service.syncCatalog();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).variantId()).isEqualTo(variantId);
        assertThat(items.get(0).upc()).isEqualTo("7700222200099");
        assertThat(items.get(0).name()).isEqualTo("POS Widget");
        assertThat(items.get(0).imageUrl()).isEqualTo("/api/v1/media/abc/content");
    }

    @Test
    void lookupByUpc_returnsMappedItem() {
        UUID tenantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku("POS-1");
        variant.setBarcode("7700222200099");
        variant.setPrice(new BigDecimal("4.50"));
        variant.setLifecycleStatus("ACTIVE");

        Product product = new Product();
        product.setId(productId);
        product.setName("POS Widget");

        when(variantRepository.findByTenantIdAndBarcode(tenantId, "7700222200099")).thenReturn(Optional.of(variant));
        when(productRepository.findAllById(List.of(productId))).thenReturn(List.of(product));
        when(productMediaRepository.findByTenantIdAndVariantIdInAndPrimaryTrue(tenantId, List.of(variantId)))
                .thenReturn(List.of());

        PosCatalogItem item = service.lookupByUpc("7700222200099");
        assertThat(item.sku()).isEqualTo("POS-1");
        assertThat(item.retailPrice()).isEqualByComparingTo("4.50");
    }

    @Test
    void lookupByUpc_rejectsBlank() {
        assertThatThrownBy(() -> service.lookupByUpc("  "))
                .isInstanceOf(ApiException.class);
    }

    private static MeResponse me(UUID tenantId, String locale, String tier) {
        return new MeResponse(
                UUID.randomUUID(),
                tenantId,
                "owner@demo.test",
                "Owner",
                List.of("OWNER"),
                List.of(),
                null,
                null,
                null,
                "America/New_York",
                locale,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                List.of(AppModule.RETAIL_POS),
                tier,
                30);
    }
}
