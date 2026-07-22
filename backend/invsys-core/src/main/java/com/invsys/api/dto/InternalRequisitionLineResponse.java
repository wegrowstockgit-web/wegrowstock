package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InternalRequisitionLineResponse(
        UUID id,
        UUID variantId,
        String sku,
        BigDecimal qtyRequested,
        BigDecimal qtyIssued
) {
}
