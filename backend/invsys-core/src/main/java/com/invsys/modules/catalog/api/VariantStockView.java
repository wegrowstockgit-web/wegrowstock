package com.invsys.modules.catalog.api;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * On-hand / allocated totals for the catalog list. Implemented outside catalog
 * so the catalog module never imports inventory repositories.
 */
public interface VariantStockView {

    StockTotals totals();

    record StockTotals(Map<UUID, BigDecimal> onHandByVariant, Map<UUID, BigDecimal> allocatedByVariant) {
    }
}
