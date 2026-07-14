package com.invsys.integration.handler;

import com.invsys.integration.IntegrationRateLimiter;
import com.invsys.integration.OutboxEventHandler;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
public class EasyPostLabelHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(EasyPostLabelHandler.class);

    private final IntegrationSyncLogRepository syncLogRepository;
    private final IntegrationRateLimiter rateLimiter;

    public EasyPostLabelHandler(IntegrationSyncLogRepository syncLogRepository,
                                IntegrationRateLimiter rateLimiter) {
        this.syncLogRepository = syncLogRepository;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public String eventType() {
        return "EASYPOST_LABEL";
    }

    @Override
    @Transactional
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        rateLimiter.tryAcquire("EASYPOST", 1);

        IntegrationSyncLog syncLog = new IntegrationSyncLog();
        syncLog.setTenantId(TenantContext.requireTenantId());
        syncLog.setSystem("EASYPOST");
        syncLog.setEntityType("SHIPMENT");
        syncLog.setEntityId(aggregateId);
        syncLog.setStatus("SYNCED");
        syncLogRepository.save(syncLog);

        log.info("EasyPost label sync logged tenant={} shipmentId={}", tenantId, aggregateId);
    }
}
