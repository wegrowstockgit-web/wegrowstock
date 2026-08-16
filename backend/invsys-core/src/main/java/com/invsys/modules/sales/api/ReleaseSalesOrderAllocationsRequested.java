package com.invsys.modules.sales.api;

import java.util.UUID;

/**
 * Sales publishes this on cancel so fulfillment can release ACTIVE allocations.
 */
public record ReleaseSalesOrderAllocationsRequested(UUID orderId) {
}
