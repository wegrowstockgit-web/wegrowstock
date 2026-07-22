package com.invsys.integration.shopify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Live Shopify Admin GraphQL node. Requires {@code SHOPIFY_API_KEY} at startup.
 */
@Component
@Profile("prod")
public class LiveShopifySyncGateway implements ShopifySyncGateway, ShopifyGraphQlClient {

    private final String apiKey;
    private final String apiVersion;

    public LiveShopifySyncGateway(
            @Value("${invsys.shopify.api-key:}") String apiKey,
            @Value("${invsys.shopify.api-version:2024-10}") String apiVersion) {
        if (apiKey == null || apiKey.isBlank() || "shopify_mock_key".equals(apiKey)) {
            throw new IllegalStateException(
                    "SHOPIFY_API_KEY (invsys.shopify.api-key) must be configured for production profile");
        }
        this.apiKey = apiKey;
        this.apiVersion = apiVersion;
    }

    @Override
    public String endpoint() {
        return "https://admin.shopify.com/api/" + apiVersion + "/graphql.json";
    }

    @Override
    public boolean isLive() {
        return true;
    }

    public String apiKey() {
        return apiKey;
    }
}
