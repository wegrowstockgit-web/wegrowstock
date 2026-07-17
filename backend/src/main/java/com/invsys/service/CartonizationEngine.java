package com.invsys.service;

import com.invsys.common.ApiException;
import com.invsys.domain.ShippingCarton;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * First-Fit Decreasing 3D bin packing over active {@link ShippingCarton} masters.
 * Units are expanded, sorted by volume descending, then placed into extreme-point
 * free spaces (axis-aligned, 6 orientations).
 */
@Component
public class CartonizationEngine {

    /** EasyPost / UPS domestic dimensional-weight divisor (in³ → lb). */
    public static final BigDecimal DIM_WEIGHT_DIVISOR = new BigDecimal("166");
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

    public record PackPlacement(
            UUID variantId,
            BigDecimal xIn,
            BigDecimal yIn,
            BigDecimal zIn,
            BigDecimal lengthIn,
            BigDecimal widthIn,
            BigDecimal heightIn
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
            BigDecimal heightIn,
            List<PackPlacement> packing
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

        List<Unit> units = expandUnits(lines);
        if (units.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_LINES",
                    "No order lines available to cartonize");
        }
        units.sort(Comparator.comparing(Unit::volume).reversed());

        BigDecimal totalWeightLb = units.stream()
                .map(Unit::weightLb)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVolumeCuIn = units.stream()
                .map(Unit::volume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ShippingCarton> ranked = cartons.stream()
                .filter(ShippingCarton::isActive)
                .sorted(Comparator
                        .comparing(this::cartonVolumeCuIn)
                        .thenComparing(ShippingCarton::getMaxWeight))
                .toList();

        for (ShippingCarton carton : ranked) {
            BigDecimal cL = toInches(carton.getLength(), carton.getDimUnit());
            BigDecimal cW = toInches(carton.getWidth(), carton.getDimUnit());
            BigDecimal cH = toInches(carton.getHeight(), carton.getDimUnit());
            BigDecimal emptyLb = toPounds(carton.getEmptyWeight(), carton.getWeightUnit());
            BigDecimal maxLb = toPounds(carton.getMaxWeight(), carton.getWeightUnit());
            BigDecimal packedWeight = totalWeightLb.add(emptyLb);
            if (maxLb.compareTo(packedWeight) < 0) {
                continue;
            }

            List<PackPlacement> packing = tryPackFfd(units, cL, cW, cH);
            if (packing == null) {
                continue;
            }

            BigDecimal actualWeightLb = packedWeight.setScale(2, RoundingMode.HALF_UP);
            BigDecimal volumetricWeightLb = cL.multiply(cW).multiply(cH)
                    .divide(DIM_WEIGHT_DIVISOR, 2, RoundingMode.HALF_UP);
            BigDecimal billable = actualWeightLb.max(volumetricWeightLb);
            return new CartonizationResult(
                    carton,
                    actualWeightLb,
                    volumetricWeightLb,
                    billable,
                    totalVolumeCuIn.setScale(2, RoundingMode.HALF_UP),
                    cL,
                    cW,
                    cH,
                    packing);
        }

        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_FITTING_CARTON",
                "No shipping carton fits the order volume/weight — add a larger carton");
    }

    /**
     * First-Fit Decreasing with extreme-point free rectangles (shelf-style 3D).
     * Returns null when the unit list cannot be placed in the carton.
     */
    List<PackPlacement> tryPackFfd(List<Unit> units, BigDecimal boxL, BigDecimal boxW, BigDecimal boxH) {
        List<FreeSpace> spaces = new ArrayList<>();
        spaces.add(new FreeSpace(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, boxL, boxW, boxH));
        List<PackPlacement> placed = new ArrayList<>(units.size());

        for (Unit unit : units) {
            boolean fitted = false;
            for (int orientation = 0; orientation < 6 && !fitted; orientation++) {
                BigDecimal[] dims = orient(unit.l(), unit.w(), unit.h(), orientation);
                BigDecimal ul = dims[0];
                BigDecimal uw = dims[1];
                BigDecimal uh = dims[2];
                for (int si = 0; si < spaces.size(); si++) {
                    FreeSpace space = spaces.get(si);
                    if (space.l().compareTo(ul) < 0
                            || space.w().compareTo(uw) < 0
                            || space.h().compareTo(uh) < 0) {
                        continue;
                    }
                    placed.add(new PackPlacement(
                            unit.variantId(),
                            space.x(), space.y(), space.z(),
                            ul, uw, uh));
                    spaces.remove(si);
                    // Split remaining free space (right / forward / above).
                    BigDecimal remL = space.l().subtract(ul);
                    BigDecimal remW = space.w().subtract(uw);
                    BigDecimal remH = space.h().subtract(uh);
                    if (remL.signum() > 0) {
                        spaces.add(new FreeSpace(
                                space.x().add(ul), space.y(), space.z(),
                                remL, space.w(), space.h()));
                    }
                    if (remW.signum() > 0) {
                        spaces.add(new FreeSpace(
                                space.x(), space.y().add(uw), space.z(),
                                ul, remW, space.h()));
                    }
                    if (remH.signum() > 0) {
                        spaces.add(new FreeSpace(
                                space.x(), space.y(), space.z().add(uh),
                                ul, uw, remH));
                    }
                    fitted = true;
                    break;
                }
            }
            if (!fitted) {
                return null;
            }
        }
        return placed;
    }

    private static BigDecimal[] orient(BigDecimal l, BigDecimal w, BigDecimal h, int orientation) {
        return switch (orientation) {
            case 0 -> new BigDecimal[]{l, w, h};
            case 1 -> new BigDecimal[]{l, h, w};
            case 2 -> new BigDecimal[]{w, l, h};
            case 3 -> new BigDecimal[]{w, h, l};
            case 4 -> new BigDecimal[]{h, l, w};
            default -> new BigDecimal[]{h, w, l};
        };
    }

    private List<Unit> expandUnits(List<LineItem> lines) {
        List<Unit> units = new ArrayList<>();
        for (LineItem line : lines) {
            BigDecimal qty = nullSafe(line.quantity());
            int count = qty.setScale(0, RoundingMode.CEILING).intValue();
            if (count <= 0) {
                continue;
            }
            BigDecimal l = toInches(nullSafe(line.length()), line.dimUnit());
            BigDecimal w = toInches(nullSafe(line.width()), line.dimUnit());
            BigDecimal h = toInches(nullSafe(line.height()), line.dimUnit());
            if (l.signum() <= 0 || w.signum() <= 0 || h.signum() <= 0) {
                l = w = h = new BigDecimal("6");
            }
            BigDecimal weightLb = toPounds(nullSafe(line.weight()), line.weightUnit());
            for (int i = 0; i < count; i++) {
                units.add(new Unit(line.variantId(), l, w, h, weightLb));
            }
        }
        return units;
    }

    private BigDecimal cartonVolumeCuIn(ShippingCarton c) {
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

    record Unit(UUID variantId, BigDecimal l, BigDecimal w, BigDecimal h, BigDecimal weightLb) {
        BigDecimal volume() {
            return l.multiply(w).multiply(h);
        }
    }

    private record FreeSpace(
            BigDecimal x, BigDecimal y, BigDecimal z,
            BigDecimal l, BigDecimal w, BigDecimal h
    ) {
    }
}
