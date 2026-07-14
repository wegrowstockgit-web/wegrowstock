package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bom_operations")
public class BomOperation extends TenantScopedEntity {

    @Column(name = "bom_id", nullable = false)
    private UUID bomId;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "estimated_hours", nullable = false)
    private BigDecimal estimatedHours = BigDecimal.ZERO;

    public UUID getBomId() {
        return bomId;
    }

    public void setBomId(UUID bomId) {
        this.bomId = bomId;
    }

    public UUID getOperationId() {
        return operationId;
    }

    public void setOperationId(UUID operationId) {
        this.operationId = operationId;
    }

    public BigDecimal getEstimatedHours() {
        return estimatedHours;
    }

    public void setEstimatedHours(BigDecimal estimatedHours) {
        this.estimatedHours = estimatedHours;
    }
}
