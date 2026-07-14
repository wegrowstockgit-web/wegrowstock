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

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(name = "work_center_id")
    private UUID workCenterId;

    @Column(name = "depends_on_operation_id")
    private UUID dependsOnOperationId;

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

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(int sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public UUID getWorkCenterId() {
        return workCenterId;
    }

    public void setWorkCenterId(UUID workCenterId) {
        this.workCenterId = workCenterId;
    }

    public UUID getDependsOnOperationId() {
        return dependsOnOperationId;
    }

    public void setDependsOnOperationId(UUID dependsOnOperationId) {
        this.dependsOnOperationId = dependsOnOperationId;
    }
}
