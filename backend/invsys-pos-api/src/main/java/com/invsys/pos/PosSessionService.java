package com.invsys.pos;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.ProductMedia;
import com.invsys.domain.VariantBarcode;
import com.invsys.domain.subscription.AppModule;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductMediaRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.pos.dto.PosCatalogItem;
import com.invsys.pos.dto.PosSessionResponse;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.VariantBarcodeRepository;
import com.invsys.service.CurrencyService;
import com.invsys.service.SettingsService;
import com.invsys.service.TenantSubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PosSessionService {

    private final AuthService authService;
    private final SettingsService settingsService;
    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantRepository tenantRepository;
    private final CurrencyService currencyService;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductMediaRepository productMediaRepository;
    private final VariantBarcodeRepository variantBarcodeRepository;

    public PosSessionService(
            AuthService authService,
            SettingsService settingsService,
            TenantSubscriptionService tenantSubscriptionService,
            TenantRepository tenantRepository,
            CurrencyService currencyService,
            ProductVariantRepository variantRepository,
            ProductRepository productRepository,
            ProductMediaRepository productMediaRepository,
            VariantBarcodeRepository variantBarcodeRepository) {
        this.authService = authService;
        this.settingsService = settingsService;
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.tenantRepository = tenantRepository;
        this.currencyService = currencyService;
        this.variantRepository = variantRepository;
        this.productRepository = productRepository;
        this.productMediaRepository = productMediaRepository;
        this.variantBarcodeRepository = variantBarcodeRepository;
    }

    public PosSessionResponse currentSession(String acceptLanguage, String timezone, String placeLanguage, String placeCurrency) {
        MeResponse me = authService.currentUser();
        boolean posEnabled = tenantSubscriptionService.isModuleEnabled(me.tenantId(), AppModule.RETAIL_POS);
        Map<String, Object> settings = settingsService.getSettings();

        String orgLanguage = posEnabled ? stringValue(settings.get("locale_language")) : null;
        String userLanguage = posEnabled ? me.localeLanguage() : null;
        String detectedPlaceLanguage = firstNonBlank(placeLanguage, acceptLanguage);
        String language = PosLocaleResolver.resolveLanguage(orgLanguage, userLanguage, detectedPlaceLanguage);
        String languageSource = PosLocaleResolver.languageSource(orgLanguage, userLanguage, detectedPlaceLanguage);

        String inferredPlaceCurrency = PosLocaleResolver.inferPlaceCurrency(acceptLanguage, timezone);
        String detectedPlaceCurrency = firstNonBlank(
                PosLocaleResolver.normalizeCurrency(placeCurrency), inferredPlaceCurrency);
        String wmsCurrency = stringValue(settings.get("currency"));
        String tenantBaseCurrency = firstNonBlank(
                PosLocaleResolver.normalizeCurrency(wmsCurrency), PosLocaleResolver.DEFAULT_CURRENCY);
        String entitledWms = posEnabled ? wmsCurrency : null;
        String currency = PosLocaleResolver.resolveDisplayCurrency(detectedPlaceCurrency, entitledWms);
        String currencySource = PosLocaleResolver.displayCurrencySource(detectedPlaceCurrency, entitledWms);
        BigDecimal liveExchangeRate = currencyService.quoteOrOne(tenantBaseCurrency, currency);

        String resolvedTimezone = firstNonBlank(
                timezone, me.timezonePreference(), stringValue(settings.get("timezone")));
        String taxRegionHint = PosLocaleResolver.taxRegionHint(
                acceptLanguage, resolvedTimezone, detectedPlaceCurrency);
        String localeTag = PosLocaleResolver.localeTag(language, currency, taxRegionHint);
        String companyName = tenantRepository.findById(me.tenantId())
                .map(tenant -> tenant.getName())
                .orElse("");
        String tier = me.tier() != null
                ? me.tier()
                : tenantSubscriptionService.getCommercialTier(me.tenantId()).name();

        return new PosSessionResponse(
                posEnabled,
                AppModule.RETAIL_POS.name(),
                tier,
                language,
                languageSource,
                currency,
                currencySource,
                PosLocaleResolver.normalizeLanguage(detectedPlaceLanguage),
                detectedPlaceCurrency,
                localeTag,
                taxRegionHint,
                resolvedTimezone,
                companyName,
                me.userId(),
                me.tenantId(),
                tenantBaseCurrency,
                liveExchangeRate);
    }

    public List<PosCatalogItem> syncCatalog() {
        UUID tenantId = TenantContext.requireTenantId();
        List<ProductVariant> variants = variantRepository
                .findByTenantIdAndLifecycleStatus(tenantId, "ACTIVE")
                .stream()
                .filter(PosSessionService::isSellable)
                .toList();
        return mapCatalog(tenantId, variants);
    }

    public PosCatalogItem lookupByUpc(String rawUpc) {
        String upc = rawUpc == null ? "" : rawUpc.trim();
        if (upc.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPC_REQUIRED", "UPC is required.");
        }
        UUID tenantId = TenantContext.requireTenantId();
        ProductVariant variant = variantRepository.findByTenantIdAndBarcode(tenantId, upc)
                .or(() -> variantBarcodeRepository.findByTenantIdAndBarcode(tenantId, upc)
                        .map(VariantBarcode::getVariantId)
                        .flatMap(variantRepository::findById)
                        .filter(found -> found.getTenantId() == null || tenantId.equals(found.getTenantId())))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "No catalog item matches UPC " + upc + "."));
        if (!"ACTIVE".equalsIgnoreCase(blankToActive(variant.getLifecycleStatus())) || variant.getPrice() == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "No catalog item matches UPC " + upc + ".");
        }
        List<PosCatalogItem> mapped = mapCatalog(tenantId, List.of(variant));
        if (!mapped.isEmpty()) {
            PosCatalogItem item = mapped.get(0);
            if (upc.equals(item.upc())) {
                return item;
            }
            return new PosCatalogItem(
                    item.variantId(), upc, item.sku(), item.name(), item.retailPrice(), item.imageUrl());
        }
        Product product = productRepository.findById(variant.getProductId()).orElse(null);
        if (product == null || product.getDeletedAt() != null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "VARIANT_NOT_FOUND", "No catalog item matches UPC " + upc + ".");
        }
        String imageUrl = productMediaRepository
                .findFirstByTenantIdAndVariantIdAndPrimaryTrue(tenantId, variant.getId())
                .map(ProductMedia::getUrl)
                .orElse(null);
        return new PosCatalogItem(
                variant.getId(),
                upc,
                variant.getSku(),
                firstNonBlank(product.getName(), variant.getSku()),
                variant.getPrice(),
                imageUrl);
    }

    private List<PosCatalogItem> mapCatalog(UUID tenantId, List<ProductVariant> variants) {
        if (variants.isEmpty()) {
            return List.of();
        }
        List<UUID> productIds = variants.stream().map(ProductVariant::getProductId).distinct().toList();
        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .filter(product -> product.getDeletedAt() == null)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        Map<UUID, String> images = productMediaRepository
                .findByTenantIdAndVariantIdInAndPrimaryTrue(tenantId, variantIds)
                .stream()
                .collect(Collectors.toMap(ProductMedia::getVariantId, ProductMedia::getUrl, (a, b) -> a));
        List<PosCatalogItem> items = new ArrayList<>(variants.size());
        for (ProductVariant variant : variants) {
            Product product = products.get(variant.getProductId());
            if (product == null) {
                continue;
            }
            String upc = firstNonBlank(variant.getBarcode());
            if (upc == null) {
                continue;
            }
            String name = firstNonBlank(product.getName(), variant.getSku());
            items.add(new PosCatalogItem(
                    variant.getId(),
                    upc,
                    variant.getSku(),
                    name,
                    variant.getPrice(),
                    images.get(variant.getId())));
        }
        return List.copyOf(items);
    }

    private static boolean isSellable(ProductVariant variant) {
        if (variant == null) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(blankToActive(variant.getLifecycleStatus()))) {
            return false;
        }
        String upc = variant.getBarcode();
        if (upc == null || upc.isBlank()) {
            return false;
        }
        return variant.getPrice() != null;
    }

    private static String blankToActive(String lifecycleStatus) {
        return lifecycleStatus == null || lifecycleStatus.isBlank() ? "ACTIVE" : lifecycleStatus;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
