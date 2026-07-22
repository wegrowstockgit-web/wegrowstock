package com.invsys.integration.handler;

import com.invsys.integration.IntegrationRateLimiter;
import com.invsys.core.integration.OutboxEventHandler;
import com.invsys.integration.shopify.ShopifyMediaSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VariantMediaUpdatedHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(VariantMediaUpdatedHandler.class);

    private final IntegrationRateLimiter rateLimiter;
    private final ShopifyMediaSyncService shopifyMediaSyncService;

    public VariantMediaUpdatedHandler(IntegrationRateLimiter rateLimiter,
                                      ShopifyMediaSyncService shopifyMediaSyncService) {
        this.rateLimiter = rateLimiter;
        this.shopifyMediaSyncService = shopifyMediaSyncService;
    }

    @Override
    public String eventType() {
        return "PRODUCT_MEDIA_UPDATED";
    }

    @Override
    public List<String> eventTypes() {
        // PRODUCT_MEDIA_UPDATED is canonical; VARIANT_MEDIA_UPDATED kept for in-flight outbox rows
        return List.of("PRODUCT_MEDIA_UPDATED", "VARIANT_MEDIA_UPDATED");
    }

    @Override
    @Transactional
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        rateLimiter.tryAcquire("SHOPIFY", 1);
        shopifyMediaSyncService.syncVariantMedia(tenantId, aggregateId, payload);
        log.info("{} processed tenant={} variant={}", eventType, tenantId, aggregateId);
    }
}
