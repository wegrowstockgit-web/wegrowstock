package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfitByProductRow(
        UUID variantId,
        String sku,
        String productName,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal marginPercent
) {
}
