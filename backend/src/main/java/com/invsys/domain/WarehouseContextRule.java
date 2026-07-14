package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "warehouse_context_rules")
public class WarehouseContextRule extends TenantScopedEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "match_type", nullable = false, length = 20)
    private String matchType;

    @Column(length = 255)
    private String ssid;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "radius_meters")
    private BigDecimal radiusMeters;

    @Column(nullable = false)
    private int priority = 100;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 255)
    private String label;

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(BigDecimal radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
