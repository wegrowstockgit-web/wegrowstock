package com.invsys.service.landedcost;

import com.invsys.modules.purchasing.domain.PurchaseOrderLine;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class WeightStrategy extends AbstractProportionStrategy {

    public WeightStrategy(Function<PurchaseOrderLine, BigDecimal> weightPerUnitFn) {
        super("weight", line -> {
            BigDecimal perUnit = weightPerUnitFn.apply(line);
            if (perUnit == null || perUnit.signum() <= 0) {
                return null;
            }
            return perUnit.multiply(receivedQty(line));
        });
    }

    public static WeightStrategy withResolvedWeights(Map<UUID, BigDecimal> weightPerUnitByLine) {
        return new WeightStrategy(line -> weightPerUnitByLine.get(line.getId()));
    }
}
