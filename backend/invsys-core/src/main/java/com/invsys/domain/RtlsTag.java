package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "rtls_tags")
public class RtlsTag extends TenantScopedEntity {

    @Column(name = "tag_id", nullable = false, length = 128)
    private String tagId;

    @Column(nullable = false, length = 20)
    private String technology;

    @Column(name = "asset_type", nullable = false, length = 30)
    private String assetType = "UNKNOWN";

    @Column(name = "asset_ref")
    private UUID assetRef;

    private String label;

    @Column(nullable = false)
    private boolean active = true;

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public UUID getAssetRef() {
        return assetRef;
    }

    public void setAssetRef(UUID assetRef) {
        this.assetRef = assetRef;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
