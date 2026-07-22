package com.invsys.api.dto;

import java.math.BigDecimal;

public record DashboardStatsResponse(
        BigDecimal stockValue,
        String currency,
        long lowStockCount,
        long openOrdersCount,
        long unpaidInvoicesCount
) {
}
