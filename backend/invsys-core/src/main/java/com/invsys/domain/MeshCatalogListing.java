package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "mesh_catalog_listings")
public class MeshCatalogListing extends TenantScopedEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "mesh_wholesale_price", precision = 19, scale = 4)
    private BigDecimal meshWholesalePrice;

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public BigDecimal getMeshWholesalePrice() {
        return meshWholesalePrice;
    }

    public void setMeshWholesalePrice(BigDecimal meshWholesalePrice) {
        this.meshWholesalePrice = meshWholesalePrice;
    }
}
