package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "manufacturing_work_centers")
public class ManufacturingWorkCenter extends TenantScopedEntity {

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "operational_status", nullable = false)
    private String operationalStatus = "ACTIVE";

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false)
    private BigDecimal capacity = BigDecimal.ONE;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOperationalStatus() {
        return operationalStatus;
    }

    public void setOperationalStatus(String operationalStatus) {
        this.operationalStatus = operationalStatus;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
    }

    /** Spec alias for operational_status (ACTIVE / MAINTENANCE / OFFLINE). */
    public String getStatus() {
        return operationalStatus;
    }
}
