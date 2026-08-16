package com.invsys.pos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One completed register sale waiting to sync from an offline outbox.
 */
public record OfflineReceiptDto(
        @NotNull UUID id,
        @NotNull UUID storeLocationId,
        @NotEmpty List<@Valid OfflineReceiptLineDto> lines,
        String tenderType,
        String taxRegion
) {
    public record OfflineReceiptLineDto(
            UUID variantId,
            String upc,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            BigDecimal unitPrice
    ) {
    }
}
