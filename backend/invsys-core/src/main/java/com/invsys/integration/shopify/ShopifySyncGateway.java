package com.invsys.integration.shopify;

/**
 * Platform Shopify GraphQL sync client — live in prod, stub outside prod.
 */
public interface ShopifySyncGateway {
    String endpoint();

    boolean isLive();
}
