package com.invsys.modules.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fulfillment-backed shipment snapshot for invoicing. Implemented outside the
 * sales module so sales never imports fulfillment repositories.
 */
public interface ShipmentInvoiceSource {

    Optional<ShipmentRef> findById(UUID shipmentId);

    List<LineQty> findLines(UUID shipmentId);

    record ShipmentRef(UUID id, UUID tenantId, UUID salesOrderId) {
    }

    record LineQty(UUID salesOrderLineId, BigDecimal quantity) {
    }
}
