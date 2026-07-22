package com.invsys.modules.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "sales_order_lines")
public class SalesOrderLine extends TenantScopedEntity {

    @Column(name = "sales_order_id", nullable = false)
    private UUID salesOrderId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "qty_ordered", nullable = false)
    private BigDecimal qtyOrdered;

    @Column(name = "qty_allocated", nullable = false)
    private BigDecimal qtyAllocated = BigDecimal.ZERO;

    @Column(name = "qty_shipped", nullable = false)
    private BigDecimal qtyShipped = BigDecimal.ZERO;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> tax = new LinkedHashMap<>();

    public UUID getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(UUID salesOrderId) {
        this.salesOrderId = salesOrderId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public BigDecimal getQtyOrdered() {
        return qtyOrdered;
    }

    public void setQtyOrdered(BigDecimal qtyOrdered) {
        this.qtyOrdered = qtyOrdered;
    }

    public BigDecimal getQtyAllocated() {
        return qtyAllocated;
    }

    public void setQtyAllocated(BigDecimal qtyAllocated) {
        this.qtyAllocated = qtyAllocated;
    }

    public BigDecimal getQtyShipped() {
        return qtyShipped;
    }

    public void setQtyShipped(BigDecimal qtyShipped) {
        this.qtyShipped = qtyShipped;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Map<String, Object> getTax() {
        return tax;
    }

    public void setTax(Map<String, Object> tax) {
        this.tax = tax;
    }
}
