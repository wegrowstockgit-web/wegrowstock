package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "boms")
public class Bom extends TenantScopedEntity {

    @Column(name = "parent_variant_id", nullable = false)
    private UUID parentVariantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "auto_assemble", nullable = false)
    private boolean autoAssemble;

    public UUID getParentVariantId() {
        return parentVariantId;
    }

    public void setParentVariantId(UUID parentVariantId) {
        this.parentVariantId = parentVariantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAutoAssemble() {
        return autoAssemble;
    }

    public void setAutoAssemble(boolean autoAssemble) {
        this.autoAssemble = autoAssemble;
    }
}
