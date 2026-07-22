package com.invsys.modules.inventory.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Bootstrap-DS flush of {@code inventory_level_deltas}, plus tenant-scoped pending reads
 * that participate in the caller's transaction (same-TX visibility).
 */
public interface InventoryLevelDeltaFlushRepository {

    /** Claim + apply pending deltas atomically (bootstrap pool, SKIP LOCKED). */
    int flushBatch(int limit);

    /**
     * Sum unapplied on-hand deltas visible to the current tenant connection
     * (includes rows inserted earlier in the same transaction).
     */
    BigDecimal sumPendingOnHand(UUID tenantId, UUID variantId, UUID locationId, UUID lotId);

    BigDecimal sumPendingOnHandForLpn(UUID tenantId, UUID lpnId);

    record ClaimedDelta(
            UUID id,
            UUID tenantId,
            UUID variantId,
            UUID locationId,
            UUID lotId,
            UUID lpnId,
            BigDecimal onHandDelta,
            UUID ownerCustomerId
    ) {
    }
}
