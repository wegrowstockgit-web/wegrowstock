package com.invsys.modules.sales.domain;

import java.util.Set;

/**
 * Sales-order lifecycle including B2B quote-to-order (RFQ).
 * Persisted as the status string on {@link SalesOrder}.
 */
public enum SalesOrderStatus {
    DRAFT,
    DRAFT_QUOTE,
    PENDING_REP_APPROVAL,
    QUOTE_READY,
    QUOTE_ACCEPTED,
    UNALLOCATED,
    CONFIRMED,
    NEEDS_REVIEW,
    PARTIALLY_ALLOCATED,
    ALLOCATED,
    BACKORDERED,
    PICKING,
    PARTIALLY_SHIPPED,
    SHIPPED,
    HOLD,
    CREDIT_HOLD,
    CLOSED,
    CANCELLED;

    private static final Set<String> ALLOCATABLE = Set.of(
            CONFIRMED.name(),
            BACKORDERED.name(),
            ALLOCATED.name(),
            UNALLOCATED.name(),
            PARTIALLY_ALLOCATED.name());

    private static final Set<String> QUOTE_EDITABLE = Set.of(
            PENDING_REP_APPROVAL.name(),
            QUOTE_READY.name());

    public static boolean canAllocate(String status) {
        return status != null && ALLOCATABLE.contains(status);
    }

    public static boolean canEditQuote(String status) {
        return status != null && QUOTE_EDITABLE.contains(status);
    }

    public static boolean isQuoteInbox(String status) {
        return PENDING_REP_APPROVAL.name().equals(status);
    }

    public static boolean isBackorderVisible(String status) {
        return PARTIALLY_ALLOCATED.name().equals(status) || BACKORDERED.name().equals(status)
                || UNALLOCATED.name().equals(status);
    }
}
