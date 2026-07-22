package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "variant_barcodes")
public class VariantBarcode extends TenantScopedEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(nullable = false, length = 100)
    private String barcode;

    @Column(nullable = false, length = 30)
    private String symbology = "OTHER";

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSymbology() {
        return symbology;
    }

    public void setSymbology(String symbology) {
        this.symbology = symbology;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
