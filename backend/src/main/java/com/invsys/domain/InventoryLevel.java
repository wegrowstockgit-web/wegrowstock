package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_levels")
public class InventoryLevel extends TenantScopedEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "on_hand", nullable = false)
    private BigDecimal onHand = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal allocated = BigDecimal.ZERO;

    @Column(name = "owner_customer_id")
    private UUID ownerCustomerId;

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

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public BigDecimal getOnHand() {
        return onHand;
    }

    public void setOnHand(BigDecimal onHand) {
        this.onHand = onHand;
    }

    public BigDecimal getAllocated() {
        return allocated;
    }

    public void setAllocated(BigDecimal allocated) {
        this.allocated = allocated;
    }

    public BigDecimal getAvailable() {
        return onHand.subtract(allocated);
    }

    public UUID getOwnerCustomerId() {
        return ownerCustomerId;
    }

    public void setOwnerCustomerId(UUID ownerCustomerId) {
        this.ownerCustomerId = ownerCustomerId;
    }
}
