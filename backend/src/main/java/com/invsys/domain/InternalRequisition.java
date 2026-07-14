package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "internal_requisitions")
public class InternalRequisition extends TenantScopedEntity {

    @Column(name = "requisition_number", nullable = false, length = 50)
    private String requisitionNumber;

    @Column(name = "cost_center_id", nullable = false)
    private UUID costCenterId;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(nullable = false, length = 30)
    private String status = "DRAFT";

    public String getRequisitionNumber() {
        return requisitionNumber;
    }

    public void setRequisitionNumber(String requisitionNumber) {
        this.requisitionNumber = requisitionNumber;
    }

    public UUID getCostCenterId() {
        return costCenterId;
    }

    public void setCostCenterId(UUID costCenterId) {
        this.costCenterId = costCenterId;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public void setRequestedByUserId(UUID requestedByUserId) {
        this.requestedByUserId = requestedByUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
