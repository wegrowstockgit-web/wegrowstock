package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder extends TenantScopedEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "expected_at")
    private Instant expectedAt;

    @Column(name = "destination_location_id")
    private UUID destinationLocationId;

    @Column(name = "freight_amount", nullable = false)
    private java.math.BigDecimal freightAmount = java.math.BigDecimal.ZERO;

    @Column(name = "duties_amount", nullable = false)
    private java.math.BigDecimal dutiesAmount = java.math.BigDecimal.ZERO;

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getExpectedAt() {
        return expectedAt;
    }

    public void setExpectedAt(Instant expectedAt) {
        this.expectedAt = expectedAt;
    }

    public UUID getDestinationLocationId() {
        return destinationLocationId;
    }

    public void setDestinationLocationId(UUID destinationLocationId) {
        this.destinationLocationId = destinationLocationId;
    }

    public java.math.BigDecimal getFreightAmount() {
        return freightAmount;
    }

    public void setFreightAmount(java.math.BigDecimal freightAmount) {
        this.freightAmount = freightAmount;
    }

    public java.math.BigDecimal getDutiesAmount() {
        return dutiesAmount;
    }

    public void setDutiesAmount(java.math.BigDecimal dutiesAmount) {
        this.dutiesAmount = dutiesAmount;
    }
}
