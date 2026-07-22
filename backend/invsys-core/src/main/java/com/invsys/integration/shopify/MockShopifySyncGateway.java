package com.invsys.integration.shopify;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test", "docker", "default"})
public class MockShopifySyncGateway implements ShopifySyncGateway, ShopifyGraphQlClient {

    @Override
    public String endpoint() {
        return "mock://shopify/graphql";
    }

    @Override
    public boolean isLive() {
        return false;
    }
}
