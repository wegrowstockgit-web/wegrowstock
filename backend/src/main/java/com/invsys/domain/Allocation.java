package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "allocations")
public class Allocation extends TenantScopedEntity {

    @Column(name = "sales_order_line_id")
    private UUID salesOrderLineId;

    @Column(name = "production_order_id")
    private UUID productionOrderId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "serial_number_id")
    private UUID serialNumberId;

    public UUID getSalesOrderLineId() {
        return salesOrderLineId;
    }

    public void setSalesOrderLineId(UUID salesOrderLineId) {
        this.salesOrderLineId = salesOrderLineId;
    }

    public UUID getProductionOrderId() {
        return productionOrderId;
    }

    public void setProductionOrderId(UUID productionOrderId) {
        this.productionOrderId = productionOrderId;
    }

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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UUID getSerialNumberId() {
        return serialNumberId;
    }

    public void setSerialNumberId(UUID serialNumberId) {
        this.serialNumberId = serialNumberId;
    }
}
