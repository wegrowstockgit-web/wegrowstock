package com.invsys.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CostCenterResponse(
        UUID id,
        String code,
        String name,
        BigDecimal budget,
        Instant createdAt
) {
}
