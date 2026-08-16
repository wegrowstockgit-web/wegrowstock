package com.invsys.modules.sales.domain;

/**
 * Buyer/rep preference for how ATP shortfalls are handled at allocation time.
 */
public enum AllocationPolicy {
    /** Hold the entire order until every line can be filled. */
    SHIP_COMPLETE,
    /** Ship available quantity now; remainder is backordered. */
    ALLOW_PARTIAL;

    public static AllocationPolicy fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALLOW_PARTIAL;
        }
        return AllocationPolicy.valueOf(raw.trim().toUpperCase());
    }
}
