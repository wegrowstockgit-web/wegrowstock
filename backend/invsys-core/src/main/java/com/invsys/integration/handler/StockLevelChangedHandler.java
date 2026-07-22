package com.invsys.integration.handler;

import com.invsys.integration.IntegrationRateLimiter;
import com.invsys.core.integration.OutboxEventHandler;
import com.invsys.integration.shopify.ShopifyInventorySyncService;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class StockLevelChangedHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(StockLevelChangedHandler.class);

    private final IntegrationSyncLogRepository syncLogRepository;
    private final IntegrationRateLimiter rateLimiter;
    private final ShopifyInventorySyncService shopifyInventorySyncService;

    public StockLevelChangedHandler(IntegrationSyncLogRepository syncLogRepository,
                                    IntegrationRateLimiter rateLimiter,
                                    ShopifyInventorySyncService shopifyInventorySyncService) {
        this.syncLogRepository = syncLogRepository;
        this.rateLimiter = rateLimiter;
        this.shopifyInventorySyncService = shopifyInventorySyncService;
    }

    @Override
    public String eventType() {
        return "STOCK_LEVEL_CHANGED";
    }

    @Override
    @Transactional
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        String system = stringVal(payload.get("system"), "SHOPIFY");
        rateLimiter.tryAcquire(system, 1);

        if ("SHOPIFY".equalsIgnoreCase(system)) {
            shopifyInventorySyncService.pushQuantity(tenantId, aggregateId, payload);
            log.info("Shopify inventorySetQuantities dispatched tenant={} variant={}", tenantId, aggregateId);
            return;
        }

        IntegrationSyncLog syncLog = new IntegrationSyncLog();
        syncLog.setTenantId(TenantContext.requireTenantId());
        syncLog.setSystem(system);
        syncLog.setEntityType("STOCK_LEVEL");
        syncLog.setEntityId(aggregateId);
        syncLog.setStatus("SYNCED");
        syncLogRepository.save(syncLog);

        log.info("Stock level sync logged tenant={} variant={} system={}", tenantId, aggregateId, system);
    }

    private String stringVal(Object value, String defaultValue) {
        return value != null ? value.toString() : defaultValue;
    }
}
