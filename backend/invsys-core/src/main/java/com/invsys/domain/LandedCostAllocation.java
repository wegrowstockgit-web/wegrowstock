package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "landed_cost_allocations")
public class LandedCostAllocation extends TenantScopedEntity {

    @Column(name = "supplier_invoice_id", nullable = false)
    private UUID supplierInvoiceId;

    @Column(name = "purchase_order_id", nullable = false)
    private UUID purchaseOrderId;

    @Column(name = "freight_total", nullable = false)
    private BigDecimal freightTotal;

    @Column(nullable = false, length = 20)
    private String strategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_breakdown", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> lineBreakdown = new ArrayList<>();

    public UUID getSupplierInvoiceId() {
        return supplierInvoiceId;
    }

    public void setSupplierInvoiceId(UUID supplierInvoiceId) {
        this.supplierInvoiceId = supplierInvoiceId;
    }

    public UUID getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(UUID purchaseOrderId) {
        this.purchaseOrderId = purchaseOrderId;
    }

    public BigDecimal getFreightTotal() {
        return freightTotal;
    }

    public void setFreightTotal(BigDecimal freightTotal) {
        this.freightTotal = freightTotal;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<Map<String, Object>> getLineBreakdown() {
        return lineBreakdown;
    }

    public void setLineBreakdown(List<Map<String, Object>> lineBreakdown) {
        this.lineBreakdown = lineBreakdown;
    }
}
