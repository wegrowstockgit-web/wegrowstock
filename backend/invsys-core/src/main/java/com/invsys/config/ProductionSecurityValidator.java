package com.invsys.config;

import com.invsys.integration.easypost.EasyPostGateway;
import com.invsys.integration.easypost.EasyPostProperties;
import com.invsys.integration.easypost.LiveEasyPostGateway;
import com.invsys.integration.easypost.MockEasyPostGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fail-fast when production profile is active with mock or missing secrets / live API keys.
 */
@Component
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityValidator.class);

    private static final Set<String> MOCK_SECRETS = Set.of(
            "whsec_mock_secret",
            "shopify_mock_secret",
            "easypost_mock_secret",
            "accounting_mock_secret",
            "app_user_secret",
            "app_owner_secret",
            "sk_test_mock",
            "easypost_mock_key",
            "shopify_mock_key",
            "invsyssecret",
            "invsys"
    );

    private final Environment environment;
    private final String stripeWebhookSecret;
    private final String stripePlatformWebhookSecret;
    private final String stripeSecretKey;
    private final String easyPostApiKey;
    private final String shopifyApiKey;
    private final String jwtPrivateKey;
    private final String jwtPublicKey;
    private final String integrationMasterKey;
    private final boolean publicSignupEnabled;
    private final String shopifyWebhookSecret;
    private final String easyPostWebhookSecret;
    private final String accountingWebhookSecret;
    private final String mediaSecretKey;
    private final String dbPassword;
    private final boolean cookieSecure;
    private final boolean exposeMagicToken;
    private final ObjectProvider<EasyPostGateway> easyPostGateway;
    private final EasyPostProperties easyPostProperties;

    public ProductionSecurityValidator(
            Environment environment,
            @Value("${invsys.stripe.webhook-secret:}") String stripeWebhookSecret,
            @Value("${invsys.stripe.platform-webhook-secret:}") String stripePlatformWebhookSecret,
            @Value("${invsys.stripe.secret-key:}") String stripeSecretKey,
            @Value("${invsys.easypost.api-key:}") String easyPostApiKey,
            @Value("${invsys.shopify.api-key:}") String shopifyApiKey,
            @Value("${invsys.jwt.private-key-pem:}") String jwtPrivateKey,
            @Value("${invsys.jwt.public-key-pem:}") String jwtPublicKey,
            @Value("${invsys.integration.master-key:}") String integrationMasterKey,
            @Value("${invsys.security.public-signup-enabled:false}") boolean publicSignupEnabled,
            @Value("${invsys.webhooks.shopify-secret:}") String shopifyWebhookSecret,
            @Value("${invsys.webhooks.easypost-secret:}") String easyPostWebhookSecret,
            @Value("${invsys.webhooks.accounting-secret:}") String accountingWebhookSecret,
            @Value("${invsys.media.secret-key:}") String mediaSecretKey,
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${invsys.security.cookie-secure:true}") boolean cookieSecure,
            @Value("${invsys.security.expose-magic-token:false}") boolean exposeMagicToken,
            ObjectProvider<EasyPostGateway> easyPostGateway,
            EasyPostProperties easyPostProperties) {
        this.environment = environment;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.stripePlatformWebhookSecret = stripePlatformWebhookSecret;
        this.stripeSecretKey = stripeSecretKey;
        this.easyPostApiKey = easyPostApiKey;
        this.shopifyApiKey = shopifyApiKey;
        this.jwtPrivateKey = jwtPrivateKey;
        this.jwtPublicKey = jwtPublicKey;
        this.integrationMasterKey = integrationMasterKey;
        this.publicSignupEnabled = publicSignupEnabled;
        this.shopifyWebhookSecret = shopifyWebhookSecret;
        this.easyPostWebhookSecret = easyPostWebhookSecret;
        this.accountingWebhookSecret = accountingWebhookSecret;
        this.mediaSecretKey = mediaSecretKey;
        this.dbPassword = dbPassword;
        this.cookieSecure = cookieSecure;
        this.exposeMagicToken = exposeMagicToken;
        this.easyPostGateway = easyPostGateway;
        this.easyPostProperties = easyPostProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProd()) {
            return;
        }
        List<String> errors = new ArrayList<>();
        if (isBlankOrMock(stripeWebhookSecret)) {
            errors.add("invsys.stripe.webhook-secret must be set to a non-mock value");
        }
        if (isBlankOrMock(stripePlatformWebhookSecret)) {
            errors.add("invsys.stripe.platform-webhook-secret must be set to a non-mock value");
        }
        if (isBlankOrMock(stripeSecretKey) || !stripeSecretKey.startsWith("sk_live_")) {
            errors.add("STRIPE_SECRET_KEY (invsys.stripe.secret-key) must be a live Stripe secret key (sk_live_)");
        }
        if (isBlankOrMock(easyPostApiKey)) {
            errors.add("EASYPOST_API_KEY (invsys.easypost.api-key) must be configured for production");
        }
        if (isBlankOrMock(shopifyApiKey)) {
            errors.add("SHOPIFY_API_KEY (invsys.shopify.api-key) must be configured for production");
        }
        if (jwtPrivateKey == null || jwtPrivateKey.isBlank() || jwtPublicKey == null || jwtPublicKey.isBlank()) {
            errors.add("JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be configured in production");
        }
        if (integrationMasterKey == null || integrationMasterKey.isBlank()) {
            errors.add("INTEGRATION_MASTER_KEY must be configured in production");
        }
        if (isBlankOrMock(shopifyWebhookSecret)) {
            errors.add("SHOPIFY_WEBHOOK_SECRET (invsys.webhooks.shopify-secret) must be set to a non-mock value");
        }
        if (isBlankOrMock(easyPostWebhookSecret)) {
            errors.add("EASYPOST_WEBHOOK_SECRET (invsys.webhooks.easypost-secret) must be set to a non-mock value");
        }
        if (isBlankOrMock(accountingWebhookSecret)) {
            errors.add("ACCOUNTING_WEBHOOK_SECRET (invsys.webhooks.accounting-secret) must be set to a non-mock value");
        }
        if (isBlankOrMock(mediaSecretKey)) {
            errors.add("MEDIA_SECRET_KEY must be set to a non-default value");
        }
        if (isBlankOrMock(dbPassword)) {
            errors.add("DB_APP_USER_PASSWORD / DB_APP_OWNER_PASSWORD must not use the committed local default");
        }
        String applicationName = environment.getProperty("spring.application.name", "");
        String dbUsername = environment.getProperty("spring.datasource.username", "");
        if ("invsys-admin-api".equals(applicationName)) {
            if (!"app_owner".equals(dbUsername)) {
                errors.add("Control plane spring.datasource.username must be app_owner");
            }
        } else if ("invsys-api".equals(applicationName) && !"app_user".equals(dbUsername)) {
            errors.add("Data plane spring.datasource.username must be app_user");
        }
        if (!cookieSecure) {
            errors.add("invsys.security.cookie-secure must be true in production");
        }
        if (publicSignupEnabled) {
            errors.add("invsys.security.public-signup-enabled must be false in production");
        }
        if (exposeMagicToken) {
            errors.add("invsys.security.expose-magic-token must be false in production");
        }
        if (easyPostProperties.defaultFromAddress() == null) {
            errors.add("EASYPOST_FROM_STREET1/CITY/STATE/ZIP (invsys.easypost.default-from.*) must be set for live label purchase");
        }
        EasyPostGateway gateway = easyPostGateway.getIfAvailable();
        if (gateway instanceof MockEasyPostGateway) {
            errors.add("MockEasyPostGateway must not be active in production — use LiveEasyPostGateway (profile prod)");
        } else if (!(gateway instanceof LiveEasyPostGateway)) {
            errors.add("LiveEasyPostGateway bean required in production (found: "
                    + (gateway == null ? "none" : gateway.getClass().getName()) + ")");
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Production security check failed: " + String.join("; ", errors));
        }
        log.info("Production security startup checks passed (live Stripe, EasyPost, Shopify clients required)");
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlankOrMock(String value) {
        return value == null || value.isBlank() || MOCK_SECRETS.contains(value);
    }
}
