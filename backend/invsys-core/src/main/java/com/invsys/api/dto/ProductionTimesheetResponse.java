package com.invsys.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductionTimesheetResponse(
        UUID id,
        UUID productionOrderId,
        UUID operationId,
        String operationName,
        UUID userId,
        Instant startTime,
        Instant endTime,
        BigDecimal totalCost
) {
}
