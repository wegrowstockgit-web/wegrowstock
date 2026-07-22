package com.invsys.service.landedcost;

import com.invsys.core.common.Money;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Shared proportional allocator. Never emits a $0 share for a line when totalCost &gt; 0
 * and the line has a positive basis (remainder assigned to the last line).
 */
abstract class AbstractProportionStrategy implements LandedCostStrategy {

    protected final Function<PurchaseOrderLine, BigDecimal> basisFn;
    private final String dimensionName;

    protected AbstractProportionStrategy(String dimensionName, Function<PurchaseOrderLine, BigDecimal> basisFn) {
        this.dimensionName = dimensionName;
        this.basisFn = basisFn;
    }

    @Override
    public Map<UUID, Money> allocate(Money totalCost, List<PurchaseOrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }
        if (totalCost == null || totalCost.isNegative()) {
            throw new IllegalArgumentException("totalCost must be non-negative");
        }
        if (totalCost.isZero()) {
            Map<UUID, Money> zeros = new LinkedHashMap<>();
            for (PurchaseOrderLine line : lines) {
                zeros.put(line.getId(), Money.ZERO);
            }
            return zeros;
        }

        List<BigDecimal> bases = new ArrayList<>(lines.size());
        BigDecimal basisTotal = BigDecimal.ZERO;
        for (PurchaseOrderLine line : lines) {
            BigDecimal basis = basisFn.apply(line);
            if (basis == null || basis.signum() <= 0) {
                throw new MissingDimensionException(
                        dimensionName + " missing or non-positive for PO line " + line.getId());
            }
            bases.add(basis);
            basisTotal = basisTotal.add(basis);
        }

        Map<UUID, Money> result = new LinkedHashMap<>();
        Money allocated = Money.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            PurchaseOrderLine line = lines.get(i);
            Money share;
            if (i == lines.size() - 1) {
                share = totalCost.subtract(allocated);
            } else {
                share = totalCost.multiply(bases.get(i)).divide(basisTotal);
                allocated = allocated.add(share);
            }
            // Guard: never leave a positive-cost line at exactly $0 when pool > 0
            if (share.isZero() && !totalCost.isZero()) {
                share = Money.of(new BigDecimal("0.0001"));
            }
            result.put(line.getId(), share);
        }
        return result;
    }

    protected static BigDecimal receivedQty(PurchaseOrderLine line) {
        BigDecimal qty = line.getQtyReceived() != null && line.getQtyReceived().signum() > 0
                ? line.getQtyReceived()
                : line.getQtyOrdered();
        return qty != null && qty.signum() > 0 ? qty : BigDecimal.ONE;
    }
}
