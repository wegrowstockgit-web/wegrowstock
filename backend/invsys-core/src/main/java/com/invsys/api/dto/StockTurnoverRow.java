package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StockTurnoverRow(
        UUID variantId,
        String sku,
        String productName,
        BigDecimal unitsShipped,
        BigDecimal averageOnHand,
        BigDecimal turnoverRate
) {
}
