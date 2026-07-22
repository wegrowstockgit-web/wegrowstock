package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record InventoryValuationReport(
        BigDecimal grandTotal,
        String currency,
        List<InventoryValuationRow> rows
) {
}
