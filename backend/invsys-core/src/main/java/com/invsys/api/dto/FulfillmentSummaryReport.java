package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record FulfillmentSummaryReport(
        BigDecimal unitsShipped30d,
        BigDecimal openOrderLines,
        BigDecimal fillRatePercent,
        List<ReportChartPoint> ordersByStatus,
        List<ReportChartPoint> shippedByWeek
) {
}
