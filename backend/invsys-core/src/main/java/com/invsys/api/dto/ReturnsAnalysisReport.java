package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReturnsAnalysisReport(
        BigDecimal totalReturns,
        BigDecimal returnRatePercent,
        List<ReportChartPoint> returnsByStatus,
        List<ReportChartPoint> dispositionBreakdown,
        List<ReturnsAnalysisRow> rows
) {
}
