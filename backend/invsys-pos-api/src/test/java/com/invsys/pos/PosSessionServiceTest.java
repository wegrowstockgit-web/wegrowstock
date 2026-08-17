package com.invsys.pos;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.domain.Tenant;
import com.invsys.domain.subscription.AppModule;
import com.invsys.domain.subscription.CommercialTier;
import com.invsys.pos.dto.PosSessionResponse;
import com.invsys.repository.TenantRepository;
import com.invsys.service.SettingsService;
import com.invsys.service.TenantSubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PosSessionServiceTest {

    @Mock AuthService authService;
    @Mock SettingsService settingsService;
    @Mock TenantSubscriptionService tenantSubscriptionService;
    @Mock TenantRepository tenantRepository;

    @InjectMocks PosSessionService service;

    @Test
    void currentSession_appliesOrganizationLanguageAndWmsCurrencyWhenPosEnabled() {
        UUID tenantId = UUID.randomUUID();
        when(authService.currentUser()).thenReturn(me(tenantId, "fr", "ENTERPRISE"));
        when(tenantSubscriptionService.isModuleEnabled(tenantId, AppModule.RETAIL_POS)).thenReturn(true);
        when(settingsService.getSettings()).thenReturn(Map.of(
                "locale_language", "es",
                "currency", "EUR",
                "timezone", "Europe/Paris"));
        Tenant tenant = new Tenant();
        tenant.setName("Demo Corp");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        PosSessionResponse session = service.currentSession("en-US", "America/Mexico_City", "en", "MXN");

        assertThat(session.posEnabled()).isTrue();
        assertThat(session.language()).isEqualTo("es");
        assertThat(session.languageSource()).isEqualTo("ORGANIZATION");
        assertThat(session.currency()).isEqualTo("EUR");
        assertThat(session.currencySource()).isEqualTo("WMS");
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

        PosSessionResponse session = service.currentSession("en-GB", "Europe/London", null, null);

        assertThat(session.language()).isEqualTo("fr");
        assertThat(session.languageSource()).isEqualTo("USER");
        assertThat(session.currency()).isEqualTo("GBP");
        assertThat(session.currencySource()).isEqualTo("PLACE");
        assertThat(session.tier()).isEqualTo("ENTERPRISE");
        assertThat(session.timezone()).isEqualTo("Europe/London");
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
                tier);
    }
}
