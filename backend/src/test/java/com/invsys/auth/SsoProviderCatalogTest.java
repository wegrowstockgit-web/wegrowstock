package com.invsys.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsoProviderCatalogTest {

    private final SsoProviderCatalog catalog = new SsoProviderCatalog();

    @Test
    void presetsIncludeMajorIdps() {
        assertThat(catalog.presets()).extracting(SsoProviderCatalog.ProviderPreset::id)
                .containsExactly("GOOGLE", "ENTRA", "OKTA");
    }

    @Test
    void inferProviderFromIssuer() {
        assertThat(catalog.inferProvider("https://accounts.google.com")).isEqualTo("GOOGLE");
        assertThat(catalog.inferProvider("https://login.microsoftonline.com/abc/v2.0")).isEqualTo("ENTRA");
        assertThat(catalog.inferProvider("https://dev-123.okta.com/oauth2/default")).isEqualTo("OKTA");
        assertThat(catalog.inferProvider("https://idp.example.com")).isEqualTo("CUSTOM");
    }
}
