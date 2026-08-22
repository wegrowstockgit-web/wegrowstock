package com.invsys.modules.purchasing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tracking_metadata", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> trackingMetadata = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "vendor_reference")
    private String vendorReference;

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

    public List<Map<String, Object>> getTrackingMetadata() {
        return trackingMetadata;
    }

    public void setTrackingMetadata(List<Map<String, Object>> trackingMetadata) {
        this.trackingMetadata = trackingMetadata != null ? trackingMetadata : new ArrayList<>();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getVendorReference() {
        return vendorReference;
    }

    public void setVendorReference(String vendorReference) {
        this.vendorReference = vendorReference;
    }
}
