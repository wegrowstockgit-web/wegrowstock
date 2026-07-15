package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bin_replenishment_rules")
public class BinReplenishmentRule extends TenantScopedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "min_quantity", nullable = false)
    private BigDecimal minQuantity;

    @Column(name = "max_quantity", nullable = false)
    private BigDecimal maxQuantity;

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }

    public void setMinQuantity(BigDecimal minQuantity) {
        this.minQuantity = minQuantity;
    }

    public BigDecimal getMaxQuantity() {
        return maxQuantity;
    }

    public void setMaxQuantity(BigDecimal maxQuantity) {
        this.maxQuantity = maxQuantity;
    }
}
