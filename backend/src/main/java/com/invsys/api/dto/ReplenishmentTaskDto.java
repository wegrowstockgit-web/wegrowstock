package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Suggested internal transfer from RESERVE → PICK_FACE for a min-qty breach.
 */
public record ReplenishmentTaskDto(
        UUID ruleId,
        UUID variantId,
        String sku,
        String variantName,
        UUID lotId,
        String lotNumber,
        UUID fromLocationId,
        String fromLocationCode,
        String fromLocationPath,
        UUID toLocationId,
        String toLocationCode,
        String toLocationPath,
        BigDecimal pickFaceOnHand,
        BigDecimal minQuantity,
        BigDecimal maxQuantity,
        BigDecimal suggestedQuantity,
        String instruction
) {
}
