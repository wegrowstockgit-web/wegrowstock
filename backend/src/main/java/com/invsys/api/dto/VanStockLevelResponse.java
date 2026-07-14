package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VanStockLevelResponse(
        UUID variantId,
        String sku,
        UUID lotId,
        BigDecimal onHand,
        BigDecimal allocated,
        BigDecimal available
) {
}
