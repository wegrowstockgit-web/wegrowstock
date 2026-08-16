package com.invsys.modules.sales.api;

import java.util.List;
import java.util.UUID;

/**
 * Sales publishes this when an order should reserve stock. Fulfillment listens
 * in-process (same transaction) and writes allocations.
 */
public record AllocateSalesOrderRequested(UUID orderId, List<UUID> locationIds) {
}
