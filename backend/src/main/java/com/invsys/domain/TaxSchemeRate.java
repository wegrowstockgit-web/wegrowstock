package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tax_scheme_rates")
public class TaxSchemeRate extends TenantScopedEntity {

    @Column(name = "tax_scheme_id", nullable = false)
    private UUID taxSchemeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal rate = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getTaxSchemeId() {
        return taxSchemeId;
    }

    public void setTaxSchemeId(UUID taxSchemeId) {
        this.taxSchemeId = taxSchemeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
