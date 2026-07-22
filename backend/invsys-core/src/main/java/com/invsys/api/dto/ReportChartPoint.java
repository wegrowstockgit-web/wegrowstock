package com.invsys.api.dto;

import java.math.BigDecimal;

public record ReportChartPoint(
        String label,
        BigDecimal value
) {
}
