package com.invsys.integration.outbox;

import com.invsys.core.integration.OutboxEventHandler;
import com.invsys.service.ValuationRecostService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Async valuation worker trigger — network-safe virtual-thread fan-out via outbox dispatch.
 */
@Component
public class CostingMethodChangedHandler implements OutboxEventHandler {

    private final ValuationRecostService valuationRecostService;

    public CostingMethodChangedHandler(ValuationRecostService valuationRecostService) {
        this.valuationRecostService = valuationRecostService;
    }

    @Override
    public String eventType() {
        return "COSTING_METHOD_CHANGED";
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        valuationRecostService.recostTenant(tenantId);
    }
}
