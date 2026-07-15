package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "supplier_user_mappings")
public class SupplierUserMapping extends TenantScopedEntity {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
