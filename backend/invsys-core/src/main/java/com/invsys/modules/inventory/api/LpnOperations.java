package com.invsys.modules.inventory.api;

import com.invsys.modules.inventory.domain.InventoryLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * License-plate queries and dispatch used by fulfillment shipping.
 */
public interface LpnOperations {

    LpnContents contents(String lpnBarcode);

    ShipLpnResult shipLpn(String lpnBarcode, UUID salesOrderId, UUID shipmentId);

    record LpnContents(
            UUID lpnId,
            String lpnBarcode,
            String status,
            UUID locationId,
            int lineCount,
            BigDecimal totalQuantity,
            List<InventoryLevel> levels
    ) {
    }

    record ShipLpnResult(
            UUID lpnId,
            String lpnBarcode,
            int linesShipped,
            List<ShippedLpnLine> lines
    ) {
    }

    record ShippedLpnLine(
            UUID variantId,
            UUID lotId,
            BigDecimal quantity,
            UUID locationId
    ) {
    }
}
