package com.invsys.service.landedcost;

/**
 * Commercial value (qty × unit cost). Reserved for Customs/Duties — never physical freight.
 */
public class ValueStrategy extends AbstractProportionStrategy {

    public ValueStrategy() {
        super("value", line -> {
            var qty = receivedQty(line);
            var unit = line.getUnitCost() != null ? line.getUnitCost() : java.math.BigDecimal.ZERO;
            var value = qty.multiply(unit);
            return value.signum() > 0 ? value : qty; // still avoid $0 when unit cost is zero
        });
    }
}
