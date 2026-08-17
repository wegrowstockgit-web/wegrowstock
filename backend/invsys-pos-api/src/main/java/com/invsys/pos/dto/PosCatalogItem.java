package com.invsys.pos.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One sellable WMS variant for the retail register cache.
 */
public record PosCatalogItem(
        UUID variantId,
        String upc,
        String sku,
        String name,
        BigDecimal retailPrice,
        String imageUrl
) {
}
