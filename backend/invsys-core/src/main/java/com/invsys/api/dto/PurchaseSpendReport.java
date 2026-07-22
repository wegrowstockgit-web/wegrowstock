package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseSpendReport(
        BigDecimal totalSpend,
        String currency,
        List<ReportChartPoint> spendBySupplier,
        List<ReportChartPoint> spendByMonth,
        List<PurchaseSpendRow> rows
) {
}
