package com.invsys.integration.outbox;

import com.invsys.core.integration.OutboxEventHandler;
import com.invsys.mesh.CrossTenantMeshBridgeService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class PurchaseOrderSubmittedMeshHandler implements OutboxEventHandler {

    private final CrossTenantMeshBridgeService meshBridgeService;

    public PurchaseOrderSubmittedMeshHandler(CrossTenantMeshBridgeService meshBridgeService) {
        this.meshBridgeService = meshBridgeService;
    }

    @Override
    public String eventType() {
        return "PURCHASE_ORDER_SUBMITTED";
    }

    @Override
    public void handle(UUID tenantId, UUID aggregateId, String eventType, Map<String, Object> payload) {
        meshBridgeService.onPurchaseOrderSubmitted(tenantId, aggregateId, payload);
    }
}
