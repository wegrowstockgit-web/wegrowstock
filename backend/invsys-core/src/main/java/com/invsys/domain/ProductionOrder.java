package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "production_orders")
public class ProductionOrder extends TenantScopedEntity {

    @Column(nullable = false)
    private String number;

    @Column(name = "parent_variant_id", nullable = false)
    private UUID parentVariantId;

    @Column(name = "qty_target", nullable = false)
    private BigDecimal qtyTarget;

    @Column(name = "qty_produced", nullable = false)
    private BigDecimal qtyProduced = BigDecimal.ZERO;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "current_work_center_id")
    private UUID currentWorkCenterId;

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public UUID getParentVariantId() {
        return parentVariantId;
    }

    public void setParentVariantId(UUID parentVariantId) {
        this.parentVariantId = parentVariantId;
    }

    public BigDecimal getQtyTarget() {
        return qtyTarget;
    }

    public void setQtyTarget(BigDecimal qtyTarget) {
        this.qtyTarget = qtyTarget;
    }

    public BigDecimal getQtyProduced() {
        return qtyProduced;
    }

    public void setQtyProduced(BigDecimal qtyProduced) {
        this.qtyProduced = qtyProduced;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UUID getCurrentWorkCenterId() {
        return currentWorkCenterId;
    }

    public void setCurrentWorkCenterId(UUID currentWorkCenterId) {
        this.currentWorkCenterId = currentWorkCenterId;
    }
}
