package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

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
}
