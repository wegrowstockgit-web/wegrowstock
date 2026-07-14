package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "picking_waves")
public class PickingWave extends TenantScopedEntity {

    @Column(nullable = false)
    private String status = "DRAFT";

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
