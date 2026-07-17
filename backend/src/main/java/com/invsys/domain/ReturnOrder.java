package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

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
}
