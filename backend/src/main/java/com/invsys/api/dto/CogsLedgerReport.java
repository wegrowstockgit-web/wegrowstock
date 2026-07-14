package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CogsLedgerReport(
        BigDecimal totalCogs,
        String currency,
        List<CogsLedgerRow> rows
) {
}
