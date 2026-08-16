package com.invsys.pos;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.domain.subscription.AppModule;
import com.invsys.pos.dto.PosSessionResponse;
import com.invsys.repository.TenantRepository;
import com.invsys.service.SettingsService;
import com.invsys.service.TenantSubscriptionService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PosSessionService {

    private final AuthService authService;
    private final SettingsService settingsService;
    private final TenantSubscriptionService tenantSubscriptionService;
    private final TenantRepository tenantRepository;

    public PosSessionService(
            AuthService authService,
            SettingsService settingsService,
            TenantSubscriptionService tenantSubscriptionService,
            TenantRepository tenantRepository) {
        this.authService = authService;
        this.settingsService = settingsService;
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.tenantRepository = tenantRepository;
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
        String wmsCurrency = posEnabled ? stringValue(settings.get("currency")) : null;
        String currency = PosLocaleResolver.resolveCurrency(wmsCurrency, detectedPlaceCurrency);
        String currencySource = PosLocaleResolver.currencySource(wmsCurrency, detectedPlaceCurrency);

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
                companyName);
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
