package com.invsys.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "shipping_cartons")
public class ShippingCarton extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal length;

    @Column(nullable = false)
    private BigDecimal width;

    @Column(nullable = false)
    private BigDecimal height;

    @Column(name = "max_weight", nullable = false)
    private BigDecimal maxWeight;

    @Column(name = "empty_weight", nullable = false)
    private BigDecimal emptyWeight = BigDecimal.ZERO;

    @Column(name = "dim_unit", nullable = false)
    private String dimUnit = "in";

    @Column(name = "weight_unit", nullable = false)
    private String weightUnit = "lb";

    @Column(nullable = false)
    private boolean active = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getLength() {
        return length;
    }

    public void setLength(BigDecimal length) {
        this.length = length;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public void setWidth(BigDecimal width) {
        this.width = width;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }

    public BigDecimal getMaxWeight() {
        return maxWeight;
    }

    public void setMaxWeight(BigDecimal maxWeight) {
        this.maxWeight = maxWeight;
    }

    public BigDecimal getEmptyWeight() {
        return emptyWeight;
    }

    public void setEmptyWeight(BigDecimal emptyWeight) {
        this.emptyWeight = emptyWeight;
    }

    public String getDimUnit() {
        return dimUnit;
    }

    public void setDimUnit(String dimUnit) {
        this.dimUnit = dimUnit;
    }

    public String getWeightUnit() {
        return weightUnit;
    }

    public void setWeightUnit(String weightUnit) {
        this.weightUnit = weightUnit;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
