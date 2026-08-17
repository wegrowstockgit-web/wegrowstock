package com.invsys.pos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One POS exception (void, no-sale, override) waiting to sync from the offline audit trail.
 */
public record PosAuditEventDto(
        @NotNull UUID id,
        @NotNull Long timestamp,
        @NotBlank String cashierId,
        @NotBlank String eventType,
        @NotBlank String orderId,
        String productId,
        @NotNull @DecimalMin(value = "0.00") BigDecimal valueVoided,
        String managerOverrideId
) {
}
