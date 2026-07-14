package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "product_categories")
public class ProductCategory extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "median_weight")
    private BigDecimal medianWeight;

    @Column(name = "median_volume")
    private BigDecimal medianVolume;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getMedianWeight() {
        return medianWeight;
    }

    public void setMedianWeight(BigDecimal medianWeight) {
        this.medianWeight = medianWeight;
    }

    public BigDecimal getMedianVolume() {
        return medianVolume;
    }

    public void setMedianVolume(BigDecimal medianVolume) {
        this.medianVolume = medianVolume;
    }
}
