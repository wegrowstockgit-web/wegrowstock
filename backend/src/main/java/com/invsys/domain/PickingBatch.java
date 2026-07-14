package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "picking_batches")
public class PickingBatch extends TenantScopedEntity {

    @Column(name = "wave_id", nullable = false)
    private UUID waveId;

    @Column(name = "assigned_user_id")
    private UUID assignedUserId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(nullable = false)
    private String status = "DRAFT";

    public UUID getWaveId() {
        return waveId;
    }

    public void setWaveId(UUID waveId) {
        this.waveId = waveId;
    }

    public UUID getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(UUID assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public UUID getZoneId() {
        return zoneId;
    }

    public void setZoneId(UUID zoneId) {
        this.zoneId = zoneId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
