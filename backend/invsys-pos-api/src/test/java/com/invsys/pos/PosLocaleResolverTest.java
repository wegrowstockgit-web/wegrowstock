package com.invsys.pos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PosLocaleResolverTest {

    @Test
    void normalizeLanguage_mapsSupportedPrefixesAndIgnoresUnknown() {
        assertThat(PosLocaleResolver.normalizeLanguage("es-MX")).isEqualTo("es");
        assertThat(PosLocaleResolver.normalizeLanguage("fr_CA")).isEqualTo("fr");
        assertThat(PosLocaleResolver.normalizeLanguage("en-GB,en;q=0.9")).isEqualTo("en");
        assertThat(PosLocaleResolver.normalizeLanguage("de-DE")).isNull();
        assertThat(PosLocaleResolver.normalizeLanguage("  ")).isNull();
        assertThat(PosLocaleResolver.normalizeLanguage(null)).isNull();
    }

    @Test
    void resolveLanguage_prefersOrganizationThenUserThenPlace() {
        assertThat(PosLocaleResolver.resolveLanguage("es", "fr", "en")).isEqualTo("es");
        assertThat(PosLocaleResolver.resolveLanguage(null, "fr", "en")).isEqualTo("fr");
        assertThat(PosLocaleResolver.resolveLanguage("de", null, "es-MX")).isEqualTo("es");
        assertThat(PosLocaleResolver.resolveLanguage(null, null, null)).isEqualTo("en");
        assertThat(PosLocaleResolver.languageSource("es", "fr", "en")).isEqualTo("ORGANIZATION");
        assertThat(PosLocaleResolver.languageSource(null, "fr", "en")).isEqualTo("USER");
        assertThat(PosLocaleResolver.languageSource(null, null, "fr")).isEqualTo("PLACE");
        assertThat(PosLocaleResolver.languageSource(null, null, "de")).isEqualTo("DEFAULT");
    }

    @Test
    void resolveCurrency_placeWinsForDisplay() {
        assertThat(PosLocaleResolver.resolveCurrency("EUR", "MXN")).isEqualTo("MXN");
        assertThat(PosLocaleResolver.resolveDisplayCurrency("MXN", "EUR")).isEqualTo("MXN");
        assertThat(PosLocaleResolver.resolveCurrency(null, "gbp")).isEqualTo("GBP");
        assertThat(PosLocaleResolver.resolveCurrency("nope", null)).isEqualTo("USD");
        assertThat(PosLocaleResolver.currencySource("USD", "MXN")).isEqualTo("PLACE");
        assertThat(PosLocaleResolver.displayCurrencySource(null, "USD")).isEqualTo("WMS");
        assertThat(PosLocaleResolver.currencySource(null, "MXN")).isEqualTo("PLACE");
        assertThat(PosLocaleResolver.currencySource("12", "nope")).isEqualTo("DEFAULT");
        assertThat(PosLocaleResolver.normalizeCurrency("mxn")).isEqualTo("MXN");
        assertThat(PosLocaleResolver.normalizeCurrency("US")).isNull();
    }

    @Test
    void inferPlaceCurrency_usesTimezoneThenAcceptLanguage() {
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-US", "America/Mexico_City")).isEqualTo("MXN");
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-US", "Europe/London")).isEqualTo("GBP");
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-US", "Europe/Paris")).isEqualTo("EUR");
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-US", "America/Toronto")).isEqualTo("CAD");
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-US", "Australia/Sydney")).isEqualTo("AUD");
        assertThat(PosLocaleResolver.inferPlaceCurrency("es-MX", null)).isEqualTo("MXN");
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-GB", null)).isEqualTo("GBP");
        assertThat(PosLocaleResolver.inferPlaceCurrency("fr-CA", null)).isEqualTo("CAD");
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-AU", null)).isEqualTo("AUD");
        assertThat(PosLocaleResolver.inferPlaceCurrency("fr-FR", null)).isEqualTo("EUR");
        assertThat(PosLocaleResolver.inferPlaceCurrency("en-US", "America/New_York")).isEqualTo("USD");
    }

    @Test
    void taxRegionAndLocaleTag_followMexicoAndLanguage() {
        assertThat(PosLocaleResolver.taxRegionHint("en-US", "America/Mexico_City", "USD")).isEqualTo("MX");
        assertThat(PosLocaleResolver.taxRegionHint("en-US", "America/New_York", "MXN")).isEqualTo("MX");
        assertThat(PosLocaleResolver.taxRegionHint("es-MX", null, "USD")).isEqualTo("MX");
        assertThat(PosLocaleResolver.taxRegionHint("en-US", "America/New_York", "USD")).isEqualTo("US");
        assertThat(PosLocaleResolver.localeTag("es", "MXN", "MX")).isEqualTo("es-MX");
        assertThat(PosLocaleResolver.localeTag("en", "MXN", "MX")).isEqualTo("en-MX");
        assertThat(PosLocaleResolver.localeTag("fr", "EUR", "US")).isEqualTo("fr-FR");
        assertThat(PosLocaleResolver.localeTag("en", "GBP", "US")).isEqualTo("en-GB");
        assertThat(PosLocaleResolver.localeTag("fr", "CAD", "US")).isEqualTo("fr-CA");
        assertThat(PosLocaleResolver.localeTag("es", "USD", "US")).isEqualTo("es-ES");
        assertThat(PosLocaleResolver.localeTag("en", "USD", "US")).isEqualTo("en-US");
        assertThat(PosLocaleResolver.localeTag(null, "USD", "US")).isEqualTo("en-US");
    }
}
