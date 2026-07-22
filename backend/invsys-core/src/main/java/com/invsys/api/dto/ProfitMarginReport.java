package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProfitMarginReport(
        BigDecimal totalRevenue,
        BigDecimal totalCogs,
        BigDecimal grossProfit,
        BigDecimal grossMarginPercent,
        String currency,
        List<ReportChartPoint> revenueByMonth,
        List<ReportChartPoint> profitByMonth,
        List<ProfitByProductRow> byProduct,
        List<ProfitByCustomerRow> byCustomer
) {
}
