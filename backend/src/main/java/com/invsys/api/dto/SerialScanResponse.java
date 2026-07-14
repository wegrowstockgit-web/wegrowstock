package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SerialScanResponse(
        UUID serialId,
        String serialNumber,
        UUID variantId,
        String sku,
        String productName,
        String status,
        UUID locationId,
        String locationPath,
        BigDecimal quantity
) {
}
