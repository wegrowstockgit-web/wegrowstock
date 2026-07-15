package com.invsys.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Multi-directional lot genealogy for compliance / recall workflows.
 */
public record ComplianceLotTraceResponse(
        UUID lotId,
        String lotNumber,
        UUID variantId,
        String sku,
        LotOrigin origin,
        List<LotExposure> currentExposure,
        List<LotDownstreamShipment> downstream
) {
    public record LotOrigin(
            UUID ledgerId,
            Instant receivedAt,
            BigDecimal quantity,
            UUID locationId,
            String locationCode,
            String locationPath,
            UUID purchaseOrderId,
            String purchaseOrderNumber,
            UUID purchaseOrderLineId,
            UUID supplierId,
            String supplierName
    ) {
    }

    public record LotExposure(
            UUID inventoryLevelId,
            UUID locationId,
            String locationCode,
            String locationPath,
            String locationType,
            String zoneBehavior,
            BigDecimal onHand,
            BigDecimal allocated,
            BigDecimal available
    ) {
    }

    public record LotDownstreamShipment(
            UUID ledgerId,
            Instant shippedAt,
            BigDecimal quantity,
            UUID salesOrderId,
            String salesOrderNumber,
            UUID salesOrderLineId,
            UUID customerId,
            String customerName,
            UUID shipmentId,
            String trackingNumber
    ) {
    }
}
