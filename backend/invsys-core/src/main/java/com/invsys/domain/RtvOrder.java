package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "rtv_orders")
public class RtvOrder extends TenantScopedEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(nullable = false, length = 64)
    private String number;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "debit_memo_number", length = 64)
    private String debitMemoNumber;

    @Column(name = "total_chargeback_amount", nullable = false)
    private BigDecimal totalChargebackAmount = BigDecimal.ZERO;

    @Column(length = 100)
    private String carrier;

    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;

    @Column(name = "exception_id")
    private UUID exceptionId;

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public UUID getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(UUID purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
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

    public String getDebitMemoNumber() {
        return debitMemoNumber;
    }

    public void setDebitMemoNumber(String debitMemoNumber) {
        this.debitMemoNumber = debitMemoNumber;
    }

    public BigDecimal getTotalChargebackAmount() {
        return totalChargebackAmount;
    }

    public void setTotalChargebackAmount(BigDecimal totalChargebackAmount) {
        this.totalChargebackAmount = totalChargebackAmount;
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

    public UUID getExceptionId() {
        return exceptionId;
    }

    public void setExceptionId(UUID exceptionId) {
        this.exceptionId = exceptionId;
    }
}
