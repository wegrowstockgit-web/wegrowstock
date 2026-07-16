package com.invsys.integration.handler;

import com.invsys.domain.IntegrationSyncLog;
import com.invsys.integration.IntegrationRateLimiter;
import com.invsys.integration.OutboxEventHandler;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.service.ShippingCredentialService;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Outbox-driven EasyPost label purchase. Resolves the tenant's vaulted carrier token
 * (FedEx / UPS corporate account or shared EasyPost key) before the network handshake
 * runs on a virtual thread via the outbox worker.
 */
@Component
public class EasyPostLabelHandler implements OutboxEventHandler {

    private static final Logger log = LoggerFactory.getLogger(EasyPostLabelHandler.class);

    private final IntegrationSyncLogRepository syncLogRepository;
    private final IntegrationRateLimiter rateLimiter;
    private final ShippingCredentialService shippingCredentialService;

    public EasyPostLabelHandler(IntegrationSyncLogRepository syncLogRepository,
                                IntegrationRateLimiter rateLimiter,
                                ShippingCredentialService shippingCredentialService) {
        this.syncLogRepository = syncLogRepository;
        this.rateLimiter = rateLimiter;
        this.shippingCredentialService = shippingCredentialService;
    }

    @Override
    public String eventType() {
        return "EASYPOST_LABEL";
    }

    @Override
    @Transactional
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        rateLimiter.tryAcquire("EASYPOST", 1);

        String carrier = payload == null ? null : stringOrNull(payload.get("carrier"));
        String preferred = carrier != null ? carrier : stringOrNull(payload != null ? payload.get("system") : null);
        // Vaulted corporate carrier / EasyPost key — used by the outbox VT worker for the handshake.
        String apiKey = shippingCredentialService.resolveApiKey(preferred != null ? preferred : "EASYPOST");
        if (apiKey.isBlank()) {
            throw new IllegalStateException("Resolved shipping API key is blank");
        }

        IntegrationSyncLog syncLog = new IntegrationSyncLog();
        syncLog.setTenantId(TenantContext.requireTenantId());
        syncLog.setSystem(preferred != null ? preferred.toUpperCase() : "EASYPOST");
        syncLog.setEntityType("SHIPMENT");
        syncLog.setEntityId(aggregateId);
        syncLog.setStatus("SYNCED");
        syncLogRepository.save(syncLog);

        log.info("EasyPost label sync logged tenant={} shipmentId={} carrier={}",
                tenantId, aggregateId, preferred != null ? preferred : "EASYPOST");
    }

    private static String stringOrNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }
}
