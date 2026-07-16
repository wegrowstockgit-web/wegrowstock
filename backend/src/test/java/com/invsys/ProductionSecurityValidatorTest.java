package com.invsys;

import com.invsys.config.ProductionSecurityValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

    @Test
    void prodProfileFailsWhenLiveApiKeysMissing() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env,
                "whsec_live_real",
                "whsec_platform_live",
                "",
                "",
                "",
                "-----BEGIN PRIVATE KEY-----",
                "-----BEGIN PUBLIC KEY-----",
                "master-key-value",
                false);
        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void nonProdProfileSkipsChecks() {
        Environment env = new MockEnvironment();
        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                env,
                "whsec_mock_secret",
                "whsec_mock_secret",
                "sk_test_mock",
                "easypost_mock_key",
                "shopify_mock_key",
                "",
                "",
                "",
                true);
        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }
}
