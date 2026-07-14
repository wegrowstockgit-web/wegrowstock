package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BomLineResponse(
        UUID id,
        UUID componentVariantId,
        String componentSku,
        String componentName,
        BigDecimal quantityRequired
) {
}
