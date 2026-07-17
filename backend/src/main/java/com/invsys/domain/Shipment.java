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

    @Column(name = "carton_id")
    private UUID cartonId;

    @Column(name = "carton_name")
    private String cartonName;

    private BigDecimal length;

    private BigDecimal width;

    private BigDecimal height;

    @Column(name = "volumetric_weight")
    private BigDecimal volumetricWeight;

    @Column(name = "service_level")
    private String serviceLevel;

    @Column(name = "label_file_type")
    private String labelFileType;

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

    public UUID getCartonId() {
        return cartonId;
    }

    public void setCartonId(UUID cartonId) {
        this.cartonId = cartonId;
    }

    public String getCartonName() {
        return cartonName;
    }

    public void setCartonName(String cartonName) {
        this.cartonName = cartonName;
    }

    public BigDecimal getLength() {
        return length;
    }

    public void setLength(BigDecimal length) {
        this.length = length;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public void setWidth(BigDecimal width) {
        this.width = width;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }

    public BigDecimal getVolumetricWeight() {
        return volumetricWeight;
    }

    public void setVolumetricWeight(BigDecimal volumetricWeight) {
        this.volumetricWeight = volumetricWeight;
    }

    public String getServiceLevel() {
        return serviceLevel;
    }

    public void setServiceLevel(String serviceLevel) {
        this.serviceLevel = serviceLevel;
    }

    public String getLabelFileType() {
        return labelFileType;
    }

    public void setLabelFileType(String labelFileType) {
        this.labelFileType = labelFileType;
    }
}
