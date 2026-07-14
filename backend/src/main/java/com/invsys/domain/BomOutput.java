package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bom_outputs")
public class BomOutput extends TenantScopedEntity {

    @Column(name = "bom_id", nullable = false)
    private UUID bomId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "output_type", nullable = false, length = 20)
    private String outputType;

    @Column(name = "allocation_ratio", nullable = false)
    private BigDecimal allocationRatio = BigDecimal.ZERO;

    @Column(name = "qty_per_batch", nullable = false)
    private BigDecimal qtyPerBatch = BigDecimal.ONE;

    public UUID getBomId() {
        return bomId;
    }

    public void setBomId(UUID bomId) {
        this.bomId = bomId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public BigDecimal getAllocationRatio() {
        return allocationRatio;
    }

    public void setAllocationRatio(BigDecimal allocationRatio) {
        this.allocationRatio = allocationRatio;
    }

    public BigDecimal getQtyPerBatch() {
        return qtyPerBatch;
    }

    public void setQtyPerBatch(BigDecimal qtyPerBatch) {
        this.qtyPerBatch = qtyPerBatch;
    }
}
