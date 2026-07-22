package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryValuationRow(
        UUID warehouseId,
        String warehouseCode,
        String warehouseName,
        UUID variantId,
        String sku,
        String productName,
        BigDecimal onHand,
        BigDecimal avgCost,
        BigDecimal totalValue
) {
}
