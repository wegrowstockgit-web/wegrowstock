package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BinCapacityValidationService {

    private final LocationRepository locationRepository;
    private final InventoryLevelRepository levelRepository;
    private final ProductVariantRepository variantRepository;

    public BinCapacityValidationService(LocationRepository locationRepository,
                                        InventoryLevelRepository levelRepository,
                                        ProductVariantRepository variantRepository) {
        this.locationRepository = locationRepository;
        this.levelRepository = levelRepository;
        this.variantRepository = variantRepository;
    }

    public void assertFits(UUID locationId, UUID variantId, BigDecimal qty) {
        UUID tenantId = TenantContext.requireTenantId();
        Location location = locationRepository.findById(locationId)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "Location not found"));
        ProductVariant incomingVariant = variantRepository.findById(variantId)
                .filter(v -> tenantId.equals(v.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));

        if (!shouldValidateCapacity(location)) {
            return;
        }

        BigDecimal maxCubic = location.getMaxCubicCm();
        BigDecimal maxWeight = location.getMaxWeightKg();
        if (maxCubic == null && maxWeight == null) {
            return;
        }

        BigDecimal incomingQty = qty != null ? qty.abs() : BigDecimal.ZERO;
        Map<UUID, ProductVariant> variantsById = variantRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId()))
                .collect(Collectors.toMap(ProductVariant::getId, v -> v, (a, b) -> a));

        List<InventoryLevel> levels = levelRepository.findByTenantIdAndLocationId(tenantId, locationId);
        BigDecimal currentVolume = BigDecimal.ZERO;
        BigDecimal currentWeight = BigDecimal.ZERO;
        for (InventoryLevel level : levels) {
            ProductVariant variant = variantsById.get(level.getVariantId());
            if (variant == null || level.getOnHand() == null || level.getOnHand().signum() <= 0) {
                continue;
            }
            currentVolume = currentVolume.add(unitVolume(variant).multiply(level.getOnHand()));
            currentWeight = currentWeight.add(unitWeight(variant).multiply(level.getOnHand()));
        }

        BigDecimal incomingVolume = unitVolume(incomingVariant).multiply(incomingQty);
        BigDecimal incomingWeight = unitWeight(incomingVariant).multiply(incomingQty);

        if (maxCubic != null && currentVolume.add(incomingVolume).compareTo(maxCubic) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BIN_CAPACITY_EXCEEDED",
                    "Bin cubic capacity exceeded: limit " + maxCubic.stripTrailingZeros().toPlainString()
                            + " cm³, projected " + currentVolume.add(incomingVolume).setScale(4, RoundingMode.HALF_UP)
                            + " cm³");
        }
        if (maxWeight != null && currentWeight.add(incomingWeight).compareTo(maxWeight) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "BIN_CAPACITY_EXCEEDED",
                    "Bin weight capacity exceeded: limit " + maxWeight.stripTrailingZeros().toPlainString()
                            + " kg, projected " + currentWeight.add(incomingWeight).setScale(4, RoundingMode.HALF_UP)
                            + " kg");
        }
    }

    private static boolean shouldValidateCapacity(Location location) {
        if ("BIN".equalsIgnoreCase(location.getType())) {
            return true;
        }
        return location.getMaxCubicCm() != null || location.getMaxWeightKg() != null;
    }

    private static BigDecimal unitVolume(ProductVariant variant) {
        if (variant.getVolume() != null && variant.getVolume().compareTo(BigDecimal.ZERO) > 0) {
            return variant.getVolume();
        }
        BigDecimal length = variant.getLength();
        BigDecimal width = variant.getWidth();
        BigDecimal height = variant.getHeight();
        if (length != null && width != null && height != null
                && length.signum() > 0 && width.signum() > 0 && height.signum() > 0) {
            return length.multiply(width).multiply(height);
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal unitWeight(ProductVariant variant) {
        return variant.getWeight() != null ? variant.getWeight() : BigDecimal.ZERO;
    }
}
