package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment extends TenantScopedEntity {

    @Column(name = "sales_order_id", nullable = false)
    private UUID salesOrderId;

    private String carrier;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "label_ref")
    private String labelRef;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "total_weight")
    private BigDecimal totalWeight;

    @Column(name = "postage_amount")
    private BigDecimal postageAmount;

    public UUID getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(UUID salesOrderId) {
        this.salesOrderId = salesOrderId;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getLabelRef() {
        return labelRef;
    }

    public void setLabelRef(String labelRef) {
        this.labelRef = labelRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(BigDecimal totalWeight) {
        this.totalWeight = totalWeight;
    }

    public BigDecimal getPostageAmount() {
        return postageAmount;
    }

    public void setPostageAmount(BigDecimal postageAmount) {
        this.postageAmount = postageAmount;
    }
}
