package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class CostingService {

    private final ProductVariantRepository variantRepository;
    private final InventoryLevelRepository levelRepository;

    public CostingService(ProductVariantRepository variantRepository,
                          InventoryLevelRepository levelRepository) {
        this.variantRepository = variantRepository;
        this.levelRepository = levelRepository;
    }

    @Transactional
    public void applyReceiveCost(UUID variantId, BigDecimal quantity, BigDecimal unitCost) {
        if (quantity == null || quantity.signum() <= 0) {
            return;
        }
        BigDecimal incomingCost = unitCost != null ? unitCost : BigDecimal.ZERO;
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));

        BigDecimal onHand = totalOnHand(variantId);
        BigDecimal currentAvg = variant.getAvgCost() != null ? variant.getAvgCost() : BigDecimal.ZERO;
        BigDecimal newOnHand = onHand.add(quantity);

        BigDecimal newAvg;
        if (newOnHand.signum() == 0) {
            newAvg = BigDecimal.ZERO;
        } else {
            newAvg = onHand.multiply(currentAvg)
                    .add(quantity.multiply(incomingCost))
                    .divide(newOnHand, 4, RoundingMode.HALF_UP);
        }
        variant.setAvgCost(newAvg);
        variantRepository.save(variant);
    }

    /**
     * Spreads a landed-cost dollar amount across current on-hand without changing quantity.
     * newAvg = (onHand * avg + allocatedAmount) / onHand
     */
    @Transactional
    public void applyLandedCostAmount(UUID variantId, BigDecimal allocatedAmount) {
        if (allocatedAmount == null || allocatedAmount.signum() == 0) {
            return;
        }
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        BigDecimal onHand = totalOnHand(variantId);
        if (onHand.signum() <= 0) {
            // No stock yet — park freight into avg_cost as absolute bump for next receive basis
            BigDecimal current = variant.getAvgCost() != null ? variant.getAvgCost() : BigDecimal.ZERO;
            variant.setAvgCost(current.add(allocatedAmount).setScale(4, RoundingMode.HALF_UP));
            variantRepository.save(variant);
            return;
        }
        BigDecimal currentAvg = variant.getAvgCost() != null ? variant.getAvgCost() : BigDecimal.ZERO;
        BigDecimal newAvg = onHand.multiply(currentAvg)
                .add(allocatedAmount)
                .divide(onHand, 4, RoundingMode.HALF_UP);
        variant.setAvgCost(newAvg);
        variantRepository.save(variant);
    }

    @Transactional(readOnly = true)
    public BigDecimal snapshotShipCost(UUID variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Variant not found"));
        return variant.getAvgCost() != null ? variant.getAvgCost() : BigDecimal.ZERO;
    }

    private BigDecimal totalOnHand(UUID variantId) {
        List<InventoryLevel> levels = levelRepository.findByTenantIdAndVariantId(
                TenantContext.requireTenantId(), variantId);
        return levels.stream()
                .map(InventoryLevel::getOnHand)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
