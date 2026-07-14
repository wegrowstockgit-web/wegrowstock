package com.invsys.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fail-fast when production profile is active with mock or missing secrets.
 */
@Component
public class ProductionSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSecurityValidator.class);

    private static final Set<String> MOCK_SECRETS = Set.of(
            "whsec_mock_secret",
            "shopify_mock_secret",
            "easypost_mock_secret",
            "app_user_secret",
            "app_owner_secret"
    );

    private final Environment environment;
    private final String stripeWebhookSecret;
    private final String jwtPrivateKey;
    private final String jwtPublicKey;
    private final String integrationMasterKey;
    private final boolean publicSignupEnabled;

    public ProductionSecurityValidator(
            Environment environment,
            @Value("${invsys.stripe.webhook-secret:}") String stripeWebhookSecret,
            @Value("${invsys.jwt.private-key-pem:}") String jwtPrivateKey,
            @Value("${invsys.jwt.public-key-pem:}") String jwtPublicKey,
            @Value("${invsys.integration.master-key:}") String integrationMasterKey,
            @Value("${invsys.security.public-signup-enabled:true}") boolean publicSignupEnabled) {
        this.environment = environment;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.jwtPrivateKey = jwtPrivateKey;
        this.jwtPublicKey = jwtPublicKey;
        this.integrationMasterKey = integrationMasterKey;
        this.publicSignupEnabled = publicSignupEnabled;
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
        if (jwtPrivateKey == null || jwtPrivateKey.isBlank() || jwtPublicKey == null || jwtPublicKey.isBlank()) {
            errors.add("JWT_PRIVATE_KEY and JWT_PUBLIC_KEY must be configured in production");
        }
        if (integrationMasterKey == null || integrationMasterKey.isBlank()) {
            errors.add("INTEGRATION_MASTER_KEY must be configured in production");
        }
        String shopify = System.getenv().getOrDefault("SHOPIFY_WEBHOOK_SECRET", "");
        if (isBlankOrMock(shopify)) {
            errors.add("SHOPIFY_WEBHOOK_SECRET must be set to a non-mock value");
        }
        String easyPost = System.getenv().getOrDefault("EASYPOST_WEBHOOK_SECRET", "");
        if (isBlankOrMock(easyPost)) {
            errors.add("EASYPOST_WEBHOOK_SECRET must be set to a non-mock value");
        }
        if (publicSignupEnabled) {
            log.warn("Public signup is enabled in production — set invsys.security.public-signup-enabled=false if unintended");
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Production security check failed: " + String.join("; ", errors));
        }
        log.info("Production security startup checks passed");
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
