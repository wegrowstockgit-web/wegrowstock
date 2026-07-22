package com.invsys.service.landedcost;

import com.invsys.modules.purchasing.domain.PurchaseOrderLine;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public class VolumeStrategy extends AbstractProportionStrategy {

    public VolumeStrategy(Function<PurchaseOrderLine, BigDecimal> volumePerUnitFn) {
        super("volume", line -> {
            BigDecimal perUnit = volumePerUnitFn.apply(line);
            if (perUnit == null || perUnit.signum() <= 0) {
                return null;
            }
            return perUnit.multiply(receivedQty(line));
        });
    }

    /** Pre-resolved volume basis keyed by PO line id. */
    public static VolumeStrategy withResolvedVolumes(Map<UUID, BigDecimal> volumePerUnitByLine) {
        return new VolumeStrategy(line -> volumePerUnitByLine.get(line.getId()));
    }
}
