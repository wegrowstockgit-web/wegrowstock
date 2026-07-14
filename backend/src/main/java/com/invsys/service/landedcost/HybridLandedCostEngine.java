package com.invsys.service.landedcost;

import com.invsys.common.ApiException;
import com.invsys.common.Money;
import com.invsys.domain.ProductCategory;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.repository.ProductCategoryRepository;
import com.invsys.repository.ProductVariantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Hybrid waterfall (cascade) for landed costs.
 * <ul>
 *   <li>CUSTOMS_DUTY → {@link ValueStrategy} only</li>
 *   <li>FREIGHT → dimensional cascade (volume/weight + category medians → quantity subset)</li>
 * </ul>
 * Missing dimensions never default to zero (competitor margin-distortion anti-pattern).
 */
@Component
public class HybridLandedCostEngine {

    public enum CostEventType {
        FREIGHT,
        CUSTOMS_DUTY
    }

    public enum PreferredDimension {
        VOLUME,
        WEIGHT
    }

    private final ProductVariantRepository variantRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ValueStrategy valueStrategy = new ValueStrategy();
    private final QuantityStrategy quantityStrategy = new QuantityStrategy();

    public HybridLandedCostEngine(ProductVariantRepository variantRepository,
                                  ProductCategoryRepository categoryRepository) {
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
    }

    public Map<UUID, Money> allocate(Money totalCost,
                                     List<PurchaseOrderLine> lines,
                                     CostEventType eventType) {
        return allocate(totalCost, lines, eventType, PreferredDimension.WEIGHT);
    }

    public Map<UUID, Money> allocate(Money totalCost,
                                     List<PurchaseOrderLine> lines,
                                     CostEventType eventType,
                                     PreferredDimension preferredDimension) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }
        if (eventType == CostEventType.CUSTOMS_DUTY) {
            return valueStrategy.allocate(totalCost, lines);
        }
        return allocateFreightHybrid(totalCost, lines, preferredDimension);
    }

    /**
     * Explicit strategy dispatch. Value / BY_VALUE / CUSTOMS are forbidden for FREIGHT.
     */
    public Map<UUID, Money> allocateWithStrategy(Money totalCost,
                                                 List<PurchaseOrderLine> lines,
                                                 String strategyName,
                                                 CostEventType eventType) {
        String strategy = strategyName == null ? "HYBRID" : strategyName.trim().toUpperCase();

        if (eventType == CostEventType.CUSTOMS_DUTY
                || "VALUE".equals(strategy)
                || "BY_VALUE".equals(strategy)
                || "CUSTOMS".equals(strategy)) {
            if (eventType == CostEventType.FREIGHT
                    && ("VALUE".equals(strategy) || "BY_VALUE".equals(strategy))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALUE_RESERVED_FOR_CUSTOMS",
                        "ValueStrategy is reserved for Customs/Duties and cannot allocate physical freight");
            }
            return valueStrategy.allocate(totalCost, lines);
        }

        if ("HYBRID".equals(strategy)) {
            return allocateFreightHybrid(totalCost, lines, PreferredDimension.WEIGHT);
        }

        ResolvedDimensions dims = resolveDimensionsParallel(lines);
        return switch (strategy) {
            case "VOLUME" -> {
                try {
                    yield VolumeStrategy.withResolvedVolumes(dims.volumes()).allocate(totalCost, lines);
                } catch (MissingDimensionException ex) {
                    // Cascade: category already applied; fall back to quantity for full set
                    yield quantityStrategy.allocate(totalCost, lines);
                }
            }
            case "WEIGHT", "BY_WEIGHT" -> {
                try {
                    yield WeightStrategy.withResolvedWeights(dims.weights()).allocate(totalCost, lines);
                } catch (MissingDimensionException ex) {
                    yield quantityStrategy.allocate(totalCost, lines);
                }
            }
            case "QUANTITY" -> quantityStrategy.allocate(totalCost, lines);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STRATEGY",
                    "Unsupported strategy: " + strategy);
        };
    }

    private Map<UUID, Money> allocateFreightHybrid(Money totalCost,
                                                   List<PurchaseOrderLine> lines,
                                                   PreferredDimension preferred) {
        ResolvedDimensions dims = resolveDimensionsParallel(lines);

        List<PurchaseOrderLine> dimensional = new ArrayList<>();
        List<PurchaseOrderLine> quantityOnly = new ArrayList<>();
        Map<UUID, BigDecimal> dimPerUnit = new LinkedHashMap<>();
        int volumeHits = 0;
        int weightHits = 0;

        for (PurchaseOrderLine line : lines) {
            BigDecimal volume = dims.volumes().get(line.getId());
            BigDecimal weight = dims.weights().get(line.getId());
            BigDecimal primary = preferred == PreferredDimension.VOLUME ? volume : weight;
            BigDecimal secondary = preferred == PreferredDimension.VOLUME ? weight : volume;

            if (primary != null && primary.signum() > 0) {
                dimensional.add(line);
                dimPerUnit.put(line.getId(), primary);
                if (preferred == PreferredDimension.VOLUME) {
                    volumeHits++;
                } else {
                    weightHits++;
                }
            } else if (secondary != null && secondary.signum() > 0) {
                dimensional.add(line);
                dimPerUnit.put(line.getId(), secondary);
                if (preferred == PreferredDimension.VOLUME) {
                    weightHits++;
                } else {
                    volumeHits++;
                }
            } else {
                // No variant dim and no category median — QuantityStrategy for this subset
                quantityOnly.add(line);
            }
        }

        if (dimensional.isEmpty()) {
            return quantityStrategy.allocate(totalCost, lines);
        }

        boolean useVolume = volumeHits >= weightHits;
        LandedCostStrategy dimStrategy = useVolume
                ? VolumeStrategy.withResolvedVolumes(dimPerUnit)
                : WeightStrategy.withResolvedWeights(dimPerUnit);

        if (quantityOnly.isEmpty()) {
            return dimStrategy.allocate(totalCost, dimensional);
        }

        // Split pool by line count so quantity-fallback items never receive $0
        Money dimBucket = totalCost.multiply(BigDecimal.valueOf(dimensional.size()))
                .divide(BigDecimal.valueOf(lines.size()));
        Money qtyBucket = totalCost.subtract(dimBucket);

        Map<UUID, Money> result = new LinkedHashMap<>();
        result.putAll(dimStrategy.allocate(dimBucket, dimensional));
        result.putAll(quantityStrategy.allocate(qtyBucket, quantityOnly));
        return result;
    }

    /**
     * Resolve weight/volume on virtual threads: variant → category median → null (never zero).
     */
    ResolvedDimensions resolveDimensionsParallel(List<PurchaseOrderLine> lines) {
        Map<UUID, BigDecimal> volumes = new LinkedHashMap<>();
        Map<UUID, BigDecimal> weights = new LinkedHashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<LineDimension>> tasks = lines.stream()
                    .map(line -> (Callable<LineDimension>) () -> resolveLine(line))
                    .toList();
            List<Future<LineDimension>> futures = executor.invokeAll(tasks);
            for (Future<LineDimension> future : futures) {
                LineDimension dim = future.get();
                if (dim.volume() != null) {
                    volumes.put(dim.lineId(), dim.volume());
                }
                if (dim.weight() != null) {
                    weights.put(dim.lineId(), dim.weight());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ALLOCATION_INTERRUPTED",
                    "Landed-cost dimension resolve interrupted");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ALLOCATION_FAILED",
                    cause.getMessage());
        }
        return new ResolvedDimensions(volumes, weights);
    }

    private LineDimension resolveLine(PurchaseOrderLine line) {
        ProductVariant variant = variantRepository.findById(line.getVariantId()).orElse(null);
        BigDecimal volume = null;
        BigDecimal weight = null;
        ProductCategory category = null;

        if (variant != null) {
            volume = resolveVolume(variant);
            weight = positiveOrNull(variant.getWeight());
            if (variant.getCategoryId() != null) {
                category = categoryRepository.findById(variant.getCategoryId()).orElse(null);
            }
        }
        if (volume == null && category != null) {
            volume = positiveOrNull(category.getMedianVolume());
        }
        if (weight == null && category != null) {
            weight = positiveOrNull(category.getMedianWeight());
        }
        return new LineDimension(line.getId(), volume, weight);
    }

    private static BigDecimal resolveVolume(ProductVariant variant) {
        BigDecimal explicit = positiveOrNull(variant.getVolume());
        if (explicit != null) {
            return explicit;
        }
        BigDecimal l = variant.getLength();
        BigDecimal w = variant.getWidth();
        BigDecimal h = variant.getHeight();
        if (l != null && w != null && h != null
                && l.signum() > 0 && w.signum() > 0 && h.signum() > 0) {
            return l.multiply(w).multiply(h).setScale(4, RoundingMode.HALF_UP);
        }
        return null;
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }

    record LineDimension(UUID lineId, BigDecimal volume, BigDecimal weight) {
    }

    record ResolvedDimensions(Map<UUID, BigDecimal> volumes, Map<UUID, BigDecimal> weights) {
    }
}
