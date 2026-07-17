package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ShippingCarton;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Selects the smallest shipping carton that fits allocated order volume + weight.
 * Volumetric (dim) weight uses the US domestic divisor 166 for inches.
 */
@Component
public class CartonizationEngine {

    /** EasyPost / UPS domestic dimensional-weight divisor (in³ → lb). */
    public static final BigDecimal DIM_WEIGHT_DIVISOR = new BigDecimal("166");
    private static final BigDecimal PACK_FACTOR = new BigDecimal("1.10");
    private static final BigDecimal CM_TO_IN = new BigDecimal("0.393701");
    private static final BigDecimal KG_TO_LB = new BigDecimal("2.20462");

    public record LineItem(
            UUID variantId,
            BigDecimal quantity,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String dimUnit,
            BigDecimal weight,
            String weightUnit
    ) {
    }

    public record CartonizationResult(
            ShippingCarton carton,
            BigDecimal actualWeightLb,
            BigDecimal volumetricWeightLb,
            BigDecimal billableWeightLb,
            BigDecimal totalVolumeCuIn,
            BigDecimal lengthIn,
            BigDecimal widthIn,
            BigDecimal heightIn
    ) {
    }

    public CartonizationResult selectCarton(List<LineItem> lines, List<ShippingCarton> cartons) {
        if (lines == null || lines.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_LINES",
                    "No order lines available to cartonize");
        }
        if (cartons == null || cartons.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_CARTONS",
                    "No active shipping cartons configured for this tenant");
        }

        BigDecimal totalVolumeCuIn = BigDecimal.ZERO;
        BigDecimal totalWeightLb = BigDecimal.ZERO;
        BigDecimal maxLen = BigDecimal.ZERO;
        BigDecimal maxWid = BigDecimal.ZERO;
        BigDecimal maxHt = BigDecimal.ZERO;

        for (LineItem line : lines) {
            BigDecimal qty = nullSafe(line.quantity());
            if (qty.signum() <= 0) {
                continue;
            }
            BigDecimal l = toInches(nullSafe(line.length()), line.dimUnit());
            BigDecimal w = toInches(nullSafe(line.width()), line.dimUnit());
            BigDecimal h = toInches(nullSafe(line.height()), line.dimUnit());
            if (l.signum() <= 0 || w.signum() <= 0 || h.signum() <= 0) {
                // Fallback cube for undimensioned SKUs (6×6×6 in)
                l = w = h = new BigDecimal("6");
            }
            BigDecimal unitVol = l.multiply(w).multiply(h);
            totalVolumeCuIn = totalVolumeCuIn.add(unitVol.multiply(qty));
            totalWeightLb = totalWeightLb.add(toPounds(nullSafe(line.weight()), line.weightUnit()).multiply(qty));
            maxLen = maxLen.max(l);
            maxWid = maxWid.max(w);
            maxHt = maxHt.max(h);
        }

        if (totalVolumeCuIn.signum() <= 0) {
            totalVolumeCuIn = new BigDecimal("216"); // 6³
        }
        BigDecimal requiredVolume = totalVolumeCuIn.multiply(PACK_FACTOR);

        List<ShippingCarton> ranked = cartons.stream()
                .filter(ShippingCarton::isActive)
                .sorted(Comparator
                        .comparing((ShippingCarton c) -> cartonVolumeCuIn(c))
                        .thenComparing(ShippingCarton::getMaxWeight))
                .toList();

        ShippingCarton selected = null;
        for (ShippingCarton carton : ranked) {
            BigDecimal cL = toInches(carton.getLength(), carton.getDimUnit());
            BigDecimal cW = toInches(carton.getWidth(), carton.getDimUnit());
            BigDecimal cH = toInches(carton.getHeight(), carton.getDimUnit());
            BigDecimal cVol = cL.multiply(cW).multiply(cH);
            BigDecimal emptyLb = toPounds(carton.getEmptyWeight(), carton.getWeightUnit());
            BigDecimal maxLb = toPounds(carton.getMaxWeight(), carton.getWeightUnit());
            BigDecimal packedWeight = totalWeightLb.add(emptyLb);

            boolean volumeOk = cVol.compareTo(requiredVolume) >= 0;
            boolean weightOk = maxLb.compareTo(packedWeight) >= 0;
            boolean axisOk = fitsAxes(cL, cW, cH, maxLen, maxWid, maxHt);
            if (volumeOk && weightOk && axisOk) {
                selected = carton;
                break;
            }
        }
        if (selected == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_FITTING_CARTON",
                    "No shipping carton fits the order volume/weight — add a larger carton");
        }

        BigDecimal lengthIn = toInches(selected.getLength(), selected.getDimUnit());
        BigDecimal widthIn = toInches(selected.getWidth(), selected.getDimUnit());
        BigDecimal heightIn = toInches(selected.getHeight(), selected.getDimUnit());
        BigDecimal emptyLb = toPounds(selected.getEmptyWeight(), selected.getWeightUnit());
        BigDecimal actualWeightLb = totalWeightLb.add(emptyLb).setScale(2, RoundingMode.HALF_UP);
        BigDecimal volumetricWeightLb = lengthIn.multiply(widthIn).multiply(heightIn)
                .divide(DIM_WEIGHT_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal billable = actualWeightLb.max(volumetricWeightLb);

        return new CartonizationResult(
                selected,
                actualWeightLb,
                volumetricWeightLb,
                billable,
                totalVolumeCuIn.setScale(2, RoundingMode.HALF_UP),
                lengthIn,
                widthIn,
                heightIn);
    }

    private static boolean fitsAxes(BigDecimal cL, BigDecimal cW, BigDecimal cH,
                                    BigDecimal iL, BigDecimal iW, BigDecimal iH) {
        BigDecimal[] carton = sorted(cL, cW, cH);
        BigDecimal[] item = sorted(iL, iW, iH);
        return carton[0].compareTo(item[0]) >= 0
                && carton[1].compareTo(item[1]) >= 0
                && carton[2].compareTo(item[2]) >= 0;
    }

    private static BigDecimal[] sorted(BigDecimal a, BigDecimal b, BigDecimal c) {
        BigDecimal[] v = {a, b, c};
        java.util.Arrays.sort(v);
        return v;
    }

    private static BigDecimal cartonVolumeCuIn(ShippingCarton c) {
        return toInches(c.getLength(), c.getDimUnit())
                .multiply(toInches(c.getWidth(), c.getDimUnit()))
                .multiply(toInches(c.getHeight(), c.getDimUnit()));
    }

    static BigDecimal toInches(BigDecimal value, String unit) {
        if (value == null || value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        String u = unit == null ? "in" : unit.trim().toLowerCase();
        if ("cm".equals(u) || "centimeter".equals(u) || "centimeters".equals(u)) {
            return value.multiply(CM_TO_IN).setScale(4, RoundingMode.HALF_UP);
        }
        return value;
    }

    static BigDecimal toPounds(BigDecimal value, String unit) {
        if (value == null || value.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        String u = unit == null ? "lb" : unit.trim().toLowerCase();
        if ("kg".equals(u) || "kilogram".equals(u) || "kilograms".equals(u)) {
            return value.multiply(KG_TO_LB).setScale(4, RoundingMode.HALF_UP);
        }
        if ("oz".equals(u) || "ounce".equals(u) || "ounces".equals(u)) {
            return value.divide(new BigDecimal("16"), 4, RoundingMode.HALF_UP);
        }
        return value;
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
