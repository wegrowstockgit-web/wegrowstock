package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "bom_lines")
public class BomLine extends TenantScopedEntity {

    @Column(name = "bom_id", nullable = false)
    private UUID bomId;

    @Column(name = "component_variant_id", nullable = false)
    private UUID componentVariantId;

    @Column(name = "quantity_required", nullable = false)
    private BigDecimal quantityRequired;

    public UUID getBomId() {
        return bomId;
    }

    public void setBomId(UUID bomId) {
        this.bomId = bomId;
    }

    public UUID getComponentVariantId() {
        return componentVariantId;
    }

    public void setComponentVariantId(UUID componentVariantId) {
        this.componentVariantId = componentVariantId;
    }

    public BigDecimal getQuantityRequired() {
        return quantityRequired;
    }

    public void setQuantityRequired(BigDecimal quantityRequired) {
        this.quantityRequired = quantityRequired;
    }
}
