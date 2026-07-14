package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "internal_requisition_lines")
public class InternalRequisitionLine extends TenantScopedEntity {

    @Column(name = "requisition_id", nullable = false)
    private UUID requisitionId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "qty_requested", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyRequested;

    @Column(name = "qty_issued", nullable = false, precision = 19, scale = 4)
    private BigDecimal qtyIssued = BigDecimal.ZERO;

    public UUID getRequisitionId() {
        return requisitionId;
    }

    public void setRequisitionId(UUID requisitionId) {
        this.requisitionId = requisitionId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public BigDecimal getQtyRequested() {
        return qtyRequested;
    }

    public void setQtyRequested(BigDecimal qtyRequested) {
        this.qtyRequested = qtyRequested;
    }

    public BigDecimal getQtyIssued() {
        return qtyIssued;
    }

    public void setQtyIssued(BigDecimal qtyIssued) {
        this.qtyIssued = qtyIssued;
    }
}
