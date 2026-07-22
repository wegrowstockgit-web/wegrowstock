package com.invsys.service.landedcost;

import com.invsys.core.common.Money;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Allocates a landed-cost pool across purchase-order lines.
 * Keys in the result map are {@link PurchaseOrderLine#getId()}.
 */
public interface LandedCostStrategy {

    Map<UUID, Money> allocate(Money totalCost, List<PurchaseOrderLine> lines);
}
