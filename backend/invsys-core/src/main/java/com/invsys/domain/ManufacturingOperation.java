package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "manufacturing_operations")
public class ManufacturingOperation extends TenantScopedEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "default_hourly_rate", nullable = false)
    private BigDecimal defaultHourlyRate = BigDecimal.ZERO;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getDefaultHourlyRate() {
        return defaultHourlyRate;
    }

    public void setDefaultHourlyRate(BigDecimal defaultHourlyRate) {
        this.defaultHourlyRate = defaultHourlyRate;
    }
}
