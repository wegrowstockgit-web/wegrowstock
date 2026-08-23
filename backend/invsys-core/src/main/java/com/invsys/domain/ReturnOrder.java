package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "returns")
public class ReturnOrder extends TenantScopedEntity {

    @Column(name = "sales_order_id", nullable = false)
    private UUID salesOrderId;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String status = "REQUESTED";

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "return_label_url")
    private String returnLabelUrl;

    @Column(name = "estimated_label_cost")
    private BigDecimal estimatedLabelCost;

    @Column(name = "label_purchase_mode")
    private String labelPurchaseMode;

    @Column(name = "resolution_type")
    private String resolutionType;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "credit_memo_id")
    private UUID creditMemoId;

    @Column(name = "rtv_order_id")
    private UUID rtvOrderId;

    public UUID getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(UUID salesOrderId) {
        this.salesOrderId = salesOrderId;
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

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReturnLabelUrl() {
        return returnLabelUrl;
    }

    public void setReturnLabelUrl(String returnLabelUrl) {
        this.returnLabelUrl = returnLabelUrl;
    }

    public BigDecimal getEstimatedLabelCost() {
        return estimatedLabelCost;
    }

    public void setEstimatedLabelCost(BigDecimal estimatedLabelCost) {
        this.estimatedLabelCost = estimatedLabelCost;
    }

    public String getLabelPurchaseMode() {
        return labelPurchaseMode;
    }

    public void setLabelPurchaseMode(String labelPurchaseMode) {
        this.labelPurchaseMode = labelPurchaseMode;
    }

    public String getResolutionType() {
        return resolutionType;
    }

    public void setResolutionType(String resolutionType) {
        this.resolutionType = resolutionType;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public UUID getCreditMemoId() {
        return creditMemoId;
    }

    public void setCreditMemoId(UUID creditMemoId) {
        this.creditMemoId = creditMemoId;
    }

    public UUID getRtvOrderId() {
        return rtvOrderId;
    }

    public void setRtvOrderId(UUID rtvOrderId) {
        this.rtvOrderId = rtvOrderId;
    }
}
