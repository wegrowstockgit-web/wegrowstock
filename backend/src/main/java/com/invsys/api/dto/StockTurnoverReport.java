package com.invsys.api.dto;

import java.util.List;

public record StockTurnoverReport(
        String periodDays,
        List<StockTurnoverRow> rows
) {
}
