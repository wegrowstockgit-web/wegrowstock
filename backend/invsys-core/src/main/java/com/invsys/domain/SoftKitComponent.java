package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "soft_kit_components")
public class SoftKitComponent extends TenantScopedEntity {

    @Column(name = "parent_kit_id", nullable = false)
    private UUID parentKitId;

    @Column(name = "component_id", nullable = false)
    private UUID componentId;

    @Column(nullable = false)
    private BigDecimal quantity;

    public UUID getParentKitId() {
        return parentKitId;
    }

    public void setParentKitId(UUID parentKitId) {
        this.parentKitId = parentKitId;
    }

    public UUID getComponentId() {
        return componentId;
    }

    public void setComponentId(UUID componentId) {
        this.componentId = componentId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
