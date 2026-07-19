package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "rtls_position_events")
public class RtlsPositionEvent extends TenantScopedEntity {

    @Column(name = "tag_id", nullable = false, length = 128)
    private String tagId;

    @Column(nullable = false, length = 20)
    private String technology;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal x;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal y;

    @Column(precision = 19, scale = 6)
    private BigDecimal z;

    @Column(name = "accuracy_m", precision = 19, scale = 6)
    private BigDecimal accuracyM;

    @Column(name = "heading_deg", precision = 9, scale = 3)
    private BigDecimal headingDeg;

    @Column(name = "asset_type", length = 30)
    private String assetType;

    @Column(name = "asset_ref")
    private UUID assetRef;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt = Instant.now();

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public BigDecimal getX() {
        return x;
    }

    public void setX(BigDecimal x) {
        this.x = x;
    }

    public BigDecimal getY() {
        return y;
    }

    public void setY(BigDecimal y) {
        this.y = y;
    }

    public BigDecimal getZ() {
        return z;
    }

    public void setZ(BigDecimal z) {
        this.z = z;
    }

    public BigDecimal getAccuracyM() {
        return accuracyM;
    }

    public void setAccuracyM(BigDecimal accuracyM) {
        this.accuracyM = accuracyM;
    }

    public BigDecimal getHeadingDeg() {
        return headingDeg;
    }

    public void setHeadingDeg(BigDecimal headingDeg) {
        this.headingDeg = headingDeg;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public UUID getAssetRef() {
        return assetRef;
    }

    public void setAssetRef(UUID assetRef) {
        this.assetRef = assetRef;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload != null ? rawPayload : new LinkedHashMap<>();
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt != null ? observedAt : Instant.now();
    }
}
