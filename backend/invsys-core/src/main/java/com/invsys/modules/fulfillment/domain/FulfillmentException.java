package com.invsys.modules.fulfillment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "fulfillment_exceptions")
public class FulfillmentException extends TenantScopedEntity {

    @Column(name = "allocation_id", nullable = false)
    private UUID allocationId;

    @Column(name = "reported_by", nullable = false)
    private UUID reportedBy;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "resolution_status", nullable = false)
    private String resolutionStatus = "OPEN";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public UUID getAllocationId() {
        return allocationId;
    }

    public void setAllocationId(UUID allocationId) {
        this.allocationId = allocationId;
    }

    public UUID getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(UUID reportedBy) {
        this.reportedBy = reportedBy;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getResolutionStatus() {
        return resolutionStatus;
    }

    public void setResolutionStatus(String resolutionStatus) {
        this.resolutionStatus = resolutionStatus;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }
}
