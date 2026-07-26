package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "rtv_order_lines")
public class RtvOrderLine extends TenantScopedEntity {

    @Column(name = "rtv_order_id", nullable = false)
    private UUID rtvOrderId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "qty_returned", nullable = false)
    private BigDecimal qtyReturned;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "reason_code", nullable = false, length = 64)
    private String reasonCode;

    public UUID getRtvOrderId() {
        return rtvOrderId;
    }

    public void setRtvOrderId(UUID rtvOrderId) {
        this.rtvOrderId = rtvOrderId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public BigDecimal getQtyReturned() {
        return qtyReturned;
    }

    public void setQtyReturned(BigDecimal qtyReturned) {
        this.qtyReturned = qtyReturned;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }
}
