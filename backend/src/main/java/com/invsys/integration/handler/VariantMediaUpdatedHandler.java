package com.invsys.integration.handler;

import com.invsys.integration.IntegrationRateLimiter;
import com.invsys.integration.OutboxEventHandler;
import com.invsys.integration.shopify.ShopifyMediaSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        return "VARIANT_MEDIA_UPDATED";
    }

    @Override
    @Transactional
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        rateLimiter.tryAcquire("SHOPIFY", 1);
        shopifyMediaSyncService.syncVariantMedia(tenantId, aggregateId, payload);
        log.info("VARIANT_MEDIA_UPDATED processed tenant={} variant={}", tenantId, aggregateId);
    }
}
