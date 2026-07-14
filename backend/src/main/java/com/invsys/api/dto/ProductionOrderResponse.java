package com.invsys.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductionOrderResponse(
        UUID id,
        String number,
        UUID parentVariantId,
        String parentSku,
        String parentName,
        BigDecimal qtyTarget,
        BigDecimal qtyProduced,
        String status,
        Instant createdAt,
        String primaryMediaUrl
) {
}
