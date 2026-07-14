package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountMappingResponse(
        UUID id,
        String system,
        String accountType,
        String externalAccountId
) {
}
