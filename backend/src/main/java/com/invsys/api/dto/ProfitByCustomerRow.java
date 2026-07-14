package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfitByCustomerRow(
        UUID customerId,
        String customerName,
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal grossProfit,
        BigDecimal marginPercent
) {
}
