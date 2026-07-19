package com.invsys.integration.inbound;

import java.math.BigDecimal;

/**
 * Channel-agnostic order line before soft-kit explosion / persistence.
 */
public record CanonicalOrderLine(
        String sku,
        BigDecimal quantity,
        BigDecimal unitPrice
) {
}
