package com.invsys.pos;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.domain.subscription.AppModule;
import com.invsys.pos.dto.PosSessionResponse;
import com.invsys.repository.TenantRepository;
import com.invsys.service.CurrencyService;
import com.invsys.service.SettingsService;
import com.invsys.service.TenantSubscriptionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class PosSessionService {

    private final AuthService authService;
    private final SettingsService settingsService;
    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantRepository tenantRepository;
    private final CurrencyService currencyService;

    public PosSessionService(
            AuthService authService,
            SettingsService settingsService,
            TenantSubscriptionService tenantSubscriptionService,
            TenantRepository tenantRepository,
            CurrencyService currencyService) {
        this.authService = authService;
        this.settingsService = settingsService;
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.tenantRepository = tenantRepository;
        this.currencyService = currencyService;
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
