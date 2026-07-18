package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "wave_replenishment_triggers")
public class WaveReplenishmentTrigger extends TenantScopedEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "current_bin_qty", nullable = false)
    private BigDecimal currentBinQty = BigDecimal.ZERO;

    @Column(name = "projected_demand", nullable = false)
    private BigDecimal projectedDemand = BigDecimal.ZERO;

    @Column(name = "min_threshold", nullable = false)
    private BigDecimal minThreshold = BigDecimal.ZERO;

    @Column(name = "target_qty", nullable = false)
    private BigDecimal targetQty = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "ACTIVE";

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public BigDecimal getCurrentBinQty() {
        return currentBinQty;
    }

    public void setCurrentBinQty(BigDecimal currentBinQty) {
        this.currentBinQty = currentBinQty;
    }

    public BigDecimal getProjectedDemand() {
        return projectedDemand;
    }

    public void setProjectedDemand(BigDecimal projectedDemand) {
        this.projectedDemand = projectedDemand;
    }

    public BigDecimal getMinThreshold() {
        return minThreshold;
    }

    public void setMinThreshold(BigDecimal minThreshold) {
        this.minThreshold = minThreshold;
    }

    public BigDecimal getTargetQty() {
        return targetQty;
    }

    public void setTargetQty(BigDecimal targetQty) {
        this.targetQty = targetQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
