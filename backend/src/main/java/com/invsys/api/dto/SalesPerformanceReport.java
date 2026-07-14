package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record SalesPerformanceReport(
        BigDecimal totalRevenue,
        BigDecimal totalOrders,
        String currency,
        List<ReportChartPoint> revenueByMonth,
        List<ReportChartPoint> ordersByStatus,
        List<ReportChartPoint> revenueByChannel,
        List<ReportChartPoint> revenueByCustomer
) {
}
