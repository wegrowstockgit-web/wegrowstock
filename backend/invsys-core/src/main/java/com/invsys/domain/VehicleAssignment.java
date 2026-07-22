package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "vehicle_assignments")
public class VehicleAssignment extends TenantScopedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "technician_user_id", nullable = false)
    private UUID technicianUserId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    @Column(name = "returned_at")
    private Instant returnedAt;

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getTechnicianUserId() {
        return technicianUserId;
    }

    public void setTechnicianUserId(UUID technicianUserId) {
        this.technicianUserId = technicianUserId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public void setReturnedAt(Instant returnedAt) {
        this.returnedAt = returnedAt;
    }
}
