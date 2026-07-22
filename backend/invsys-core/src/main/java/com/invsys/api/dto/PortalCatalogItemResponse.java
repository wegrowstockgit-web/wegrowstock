package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortalCatalogItemResponse(
        UUID variantId,
        UUID productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        String currency,
        String primaryMediaUrl
) {
}
