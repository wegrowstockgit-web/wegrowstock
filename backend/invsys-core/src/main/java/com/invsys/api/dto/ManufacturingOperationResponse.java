package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ManufacturingOperationResponse(
        UUID id,
        String name,
        BigDecimal defaultHourlyRate
) {
}
