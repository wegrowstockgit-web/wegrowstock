package com.invsys.api.dto;

import java.util.UUID;

public record ReturnsAnalysisRow(
        UUID returnId,
        String number,
        String customerName,
        String salesOrderNumber,
        String status,
        long lineCount
) {
}
