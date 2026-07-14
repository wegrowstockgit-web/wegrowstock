package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cycle_count_lines")
public class CycleCountLine extends TenantScopedEntity {

    @Column(name = "cycle_count_id", nullable = false)
    private UUID cycleCountId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "expected_qty", nullable = false)
    private BigDecimal expectedQty = BigDecimal.ZERO;

    @Column(name = "counted_qty")
    private BigDecimal countedQty;

    public UUID getCycleCountId() {
        return cycleCountId;
    }

    public void setCycleCountId(UUID cycleCountId) {
        this.cycleCountId = cycleCountId;
    }

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public BigDecimal getExpectedQty() {
        return expectedQty;
    }

    public void setExpectedQty(BigDecimal expectedQty) {
        this.expectedQty = expectedQty;
    }

    public BigDecimal getCountedQty() {
        return countedQty;
    }

    public void setCountedQty(BigDecimal countedQty) {
        this.countedQty = countedQty;
    }
}
