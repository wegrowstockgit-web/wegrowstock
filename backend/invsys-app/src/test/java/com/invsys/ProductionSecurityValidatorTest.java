package com.invsys;

import com.invsys.config.ProductionSecurityValidator;
import com.invsys.integration.easypost.EasyPostGateway;
import com.invsys.integration.easypost.EasyPostProperties;
import com.invsys.integration.easypost.LiveEasyPostGateway;
import com.invsys.integration.easypost.MockEasyPostGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionSecurityValidatorTest {

    @Test
    void prodProfileFailsWhenLiveApiKeysMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecurityValidator validator = validator(
                env,
                "",
                "EZAK_live",
                "shopify_live",
                liveProps(),
                liveGatewayProvider());
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void prodProfileFailsWhenMockEasyPostActive() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        @SuppressWarnings("unchecked")
        ObjectProvider<EasyPostGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new MockEasyPostGateway());
        ProductionSecurityValidator validator = validator(
                env,
                "sk_live_real_key",
                "EZAK_live",
                "shopify_live",
                liveProps(),
                provider);
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MockEasyPostGateway");
    }

    @Test
    void prodProfileFailsWhenDataPlaneUsesOwnerRole() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.application.name", "invsys-api");
        env.setProperty("spring.datasource.username", "app_owner");
        ProductionSecurityValidator validator = validator(
                env,
                "sk_live_real_key",
                "EZAK_live",
                "shopify_live",
                liveProps(),
                liveGatewayProvider());
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Data plane spring.datasource.username must be app_user");
    }

    @Test
    void prodProfileFailsWhenControlPlaneUsesRestrictedRole() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.application.name", "invsys-admin-api");
        env.setProperty("spring.datasource.username", "app_user");
        ProductionSecurityValidator validator = validator(
                env,
                "sk_live_real_key",
                "EZAK_live",
                "shopify_live",
                liveProps(),
                liveGatewayProvider());
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Control plane spring.datasource.username must be app_owner");
    }

    @Test
    void prodProfileFailsWhenMagicTokenExposureEnabled() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env,
                "whsec_live_real",
                "whsec_platform_live",
                "sk_live_real_key",
                "EZAK_live",
                "shopify_live",
                "-----BEGIN PRIVATE KEY-----",
                "-----BEGIN PUBLIC KEY-----",
                "master-key-value",
                false,
                "shopify_live_whsec",
                "easypost_live_whsec",
                "accounting_live_whsec",
                "media-live-secret",
                "db-live-password",
                true,
                true,
                liveGatewayProvider(),
                liveProps());
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expose-magic-token");
    }

    @Test
    void nonProdProfileSkipsChecks() {
        Environment env = new MockEnvironment();
        @SuppressWarnings("unchecked")
        ObjectProvider<EasyPostGateway> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        ProductionSecurityValidator validator = validator(
                env,
                "sk_test_mock",
                "easypost_mock_key",
                "shopify_mock_key",
                new EasyPostProperties(),
                empty);
        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    private static ProductionSecurityValidator validator(
            Environment env,
            String stripeKey,
            String easyPostKey,
            String shopifyKey,
            EasyPostProperties props,
            ObjectProvider<EasyPostGateway> gateway) {
        return new ProductionSecurityValidator(
                env,
                "whsec_live_real",
                "whsec_platform_live",
                stripeKey,
                easyPostKey,
                shopifyKey,
                "-----BEGIN PRIVATE KEY-----",
                "-----BEGIN PUBLIC KEY-----",
                "master-key-value",
                false,
                "shopify_live_whsec",
                "easypost_live_whsec",
                "accounting_live_whsec",
                "media-live-secret",
                "db-live-password",
                true,
                false,
                gateway,
                props);
    }

    private static EasyPostProperties liveProps() {
        EasyPostProperties props = new EasyPostProperties();
        props.setApiKey("EZAK_live");
        EasyPostProperties.FromAddress from = new EasyPostProperties.FromAddress();
        from.setStreet1("100 Warehouse Way");
        from.setCity("Chicago");
        from.setState("IL");
        from.setZip("60601");
        from.setCountry("US");
        props.setDefaultFrom(from);
        return props;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<EasyPostGateway> liveGatewayProvider() {
        ObjectProvider<EasyPostGateway> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new LiveEasyPostGateway(new ObjectMapper(), liveProps()));
        return provider;
    }
}
