package com.invsys.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VehicleAssignmentResponse(
        UUID id,
        UUID locationId,
        String locationCode,
        String locationName,
        UUID technicianUserId,
        Instant assignedAt,
        Instant returnedAt
) {
}
