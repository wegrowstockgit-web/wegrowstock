package com.invsys.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "locations")
public class Location extends TenantScopedEntity {

    @Column(name = "parent_location_id")
    private UUID parentLocationId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String path;

    @Column(name = "sequence_index", nullable = false)
    private int sequenceIndex;

    /** STANDARD | PICK_FACE | RESERVE | RECEIVING */
    @Column(name = "zone_behavior", nullable = false)
    private String zoneBehavior = "STANDARD";

    @Column(name = "coord_x", precision = 19, scale = 4)
    private BigDecimal coordX;

    @Column(name = "coord_y", precision = 19, scale = 4)
    private BigDecimal coordY;

    @Column(name = "coord_z", precision = 19, scale = 4)
    private BigDecimal coordZ;

    /** Facility logistics address (street, city, state, postalCode, country). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logistics_address", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> logisticsAddress = new LinkedHashMap<>();

    /** WGS84 latitude for carrier zones / yard geofence. */
    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    /** WGS84 longitude for carrier zones / yard geofence. */
    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "gross_square_footage", precision = 19, scale = 4)
    private BigDecimal grossSquareFootage;

    @Column(name = "office_area_square_footage", precision = 19, scale = 4)
    private BigDecimal officeAreaSquareFootage;

    @Column(name = "clear_height_feet", precision = 10, scale = 2)
    private BigDecimal clearHeightFeet;

    @Column(name = "total_dock_doors")
    private Integer totalDockDoors;

    @Column(name = "weight_capacity_limit", precision = 19, scale = 4)
    private BigDecimal weightCapacityLimit;

    /** Industry-standard floor load boundary (lbs); kept in sync with weightCapacityLimit. */
    @Column(name = "floor_load_capacity_lbs", precision = 19, scale = 4)
    private BigDecimal floorLoadCapacityLbs;

    /** AMBIENT | REFRIGERATED | FROZEN — putaway temperature compliance. */
    @Column(name = "storage_temp_zone", nullable = false)
    private String storageTempZone = "AMBIENT";

    @Column(name = "allows_hazmat", nullable = false)
    private boolean allowsHazmat;

    /** Max Ti×Hi pallet positions this bin can hold; null = unlimited. */
    @Column(name = "max_pallet_positions")
    private Integer maxPalletPositions;

    public UUID getParentLocationId() {
        return parentLocationId;
    }

    public void setParentLocationId(UUID parentLocationId) {
        this.parentLocationId = parentLocationId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

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

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(int sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public String getZoneBehavior() {
        return zoneBehavior;
    }

    public void setZoneBehavior(String zoneBehavior) {
        this.zoneBehavior = zoneBehavior;
    }

    public BigDecimal getCoordX() {
        return coordX;
    }

    public void setCoordX(BigDecimal coordX) {
        this.coordX = coordX;
    }

    public BigDecimal getCoordY() {
        return coordY;
    }

    public void setCoordY(BigDecimal coordY) {
        this.coordY = coordY;
    }

    public BigDecimal getCoordZ() {
        return coordZ;
    }

    public void setCoordZ(BigDecimal coordZ) {
        this.coordZ = coordZ;
    }

    public Map<String, Object> getLogisticsAddress() {
        return logisticsAddress;
    }

    public void setLogisticsAddress(Map<String, Object> logisticsAddress) {
        this.logisticsAddress = logisticsAddress != null ? logisticsAddress : new LinkedHashMap<>();
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

    public BigDecimal getGrossSquareFootage() {
        return grossSquareFootage;
    }

    public void setGrossSquareFootage(BigDecimal grossSquareFootage) {
        this.grossSquareFootage = grossSquareFootage;
    }

    public BigDecimal getOfficeAreaSquareFootage() {
        return officeAreaSquareFootage;
    }

    public void setOfficeAreaSquareFootage(BigDecimal officeAreaSquareFootage) {
        this.officeAreaSquareFootage = officeAreaSquareFootage;
    }

    public BigDecimal getClearHeightFeet() {
        return clearHeightFeet;
    }

    public void setClearHeightFeet(BigDecimal clearHeightFeet) {
        this.clearHeightFeet = clearHeightFeet;
    }

    public Integer getTotalDockDoors() {
        return totalDockDoors;
    }

    public void setTotalDockDoors(Integer totalDockDoors) {
        this.totalDockDoors = totalDockDoors;
    }

    public BigDecimal getWeightCapacityLimit() {
        return weightCapacityLimit;
    }

    public void setWeightCapacityLimit(BigDecimal weightCapacityLimit) {
        this.weightCapacityLimit = weightCapacityLimit;
        if (weightCapacityLimit != null && this.floorLoadCapacityLbs == null) {
            this.floorLoadCapacityLbs = weightCapacityLimit;
        }
    }

    public BigDecimal getFloorLoadCapacityLbs() {
        return floorLoadCapacityLbs != null ? floorLoadCapacityLbs : weightCapacityLimit;
    }

    public void setFloorLoadCapacityLbs(BigDecimal floorLoadCapacityLbs) {
        this.floorLoadCapacityLbs = floorLoadCapacityLbs;
        if (floorLoadCapacityLbs != null) {
            this.weightCapacityLimit = floorLoadCapacityLbs;
        }
    }

    public String getStorageTempZone() {
        return storageTempZone;
    }

    public void setStorageTempZone(String storageTempZone) {
        this.storageTempZone = storageTempZone != null ? storageTempZone : "AMBIENT";
    }

    public boolean isAllowsHazmat() {
        return allowsHazmat;
    }

    public void setAllowsHazmat(boolean allowsHazmat) {
        this.allowsHazmat = allowsHazmat;
    }

    public Integer getMaxPalletPositions() {
        return maxPalletPositions;
    }

    public void setMaxPalletPositions(Integer maxPalletPositions) {
        this.maxPalletPositions = maxPalletPositions;
    }
}
