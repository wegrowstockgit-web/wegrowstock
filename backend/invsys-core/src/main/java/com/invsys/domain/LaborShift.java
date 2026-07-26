package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "labor_shifts")
public class LaborShift extends TenantScopedEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "clock_in", nullable = false)
    private Instant clockIn;

    @Column(name = "clock_out")
    private Instant clockOut;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Instant getClockIn() {
        return clockIn;
    }

    public void setClockIn(Instant clockIn) {
        this.clockIn = clockIn;
    }

    public Instant getClockOut() {
        return clockOut;
    }

    public void setClockOut(Instant clockOut) {
        this.clockOut = clockOut;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
