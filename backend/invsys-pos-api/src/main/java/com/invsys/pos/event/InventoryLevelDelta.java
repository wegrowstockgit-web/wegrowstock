package com.invsys.pos.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lock-free on-hand mutation destined for {@code inventory_level_deltas}.
 * The WMS flush worker applies these without holding a hotspot row lock
 * on {@code inventory_levels} during the POS ingest request.
 */
public record InventoryLevelDelta(
        UUID tenantId,
        UUID variantId,
        UUID locationId,
        UUID lotId,
        UUID lpnId,
        BigDecimal onHandDelta,
        UUID ownerCustomerId
) {
}
