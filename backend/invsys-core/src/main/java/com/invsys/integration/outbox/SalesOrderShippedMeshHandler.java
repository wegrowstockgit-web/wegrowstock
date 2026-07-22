package com.invsys.integration.outbox;

import com.invsys.core.integration.OutboxEventHandler;
import com.invsys.mesh.CrossTenantMeshBridgeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class SalesOrderShippedMeshHandler implements OutboxEventHandler {

    private final CrossTenantMeshBridgeService meshBridgeService;

    public SalesOrderShippedMeshHandler(CrossTenantMeshBridgeService meshBridgeService) {
        this.meshBridgeService = meshBridgeService;
    }

    @Override
    public String eventType() {
        return "SALES_ORDER_SHIPPED";
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        UUID salesOrderId = aggregateId;
        if (payload != null && payload.get("salesOrderId") != null) {
            salesOrderId = UUID.fromString(String.valueOf(payload.get("salesOrderId")));
        }
        meshBridgeService.onSalesOrderShipped(tenantId, salesOrderId, payload);
    }
}
