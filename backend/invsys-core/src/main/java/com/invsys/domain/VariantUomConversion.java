package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "variant_uom_conversions")
public class VariantUomConversion extends TenantScopedEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "uom_type", nullable = false)
    private String uomType;

    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(name = "conversion_ratio", nullable = false)
    private BigDecimal conversionRatio = BigDecimal.ONE;

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public String getUomType() {
        return uomType;
    }

    public void setUomType(String uomType) {
        this.uomType = uomType;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public BigDecimal getConversionRatio() {
        return conversionRatio;
    }

    public void setConversionRatio(BigDecimal conversionRatio) {
        this.conversionRatio = conversionRatio;
    }
}
