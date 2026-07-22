package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "picking_tasks")
public class PickingTask extends TenantScopedEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "allocation_id", nullable = false)
    private UUID allocationId;

    @Column(name = "location_path", nullable = false)
    private String locationPath;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "tote_identifier", length = 20)
    private String toteIdentifier;

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public UUID getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(UUID allocationId) {
        this.allocationId = allocationId;
    }

    public String getLocationPath() {
        return locationPath;
    }

    public void setLocationPath(String locationPath) {
        this.locationPath = locationPath;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(int sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getToteIdentifier() {
        return toteIdentifier;
    }

    public void setToteIdentifier(String toteIdentifier) {
        this.toteIdentifier = toteIdentifier;
    }
}
