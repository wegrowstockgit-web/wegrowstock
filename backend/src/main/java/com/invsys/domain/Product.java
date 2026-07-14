package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "products")
public class Product extends TenantScopedEntity {

    @Column(name = "sku_root", nullable = false)
    private String skuRoot;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public String getSkuRoot() {
        return skuRoot;
    }

    public void setSkuRoot(String skuRoot) {
        this.skuRoot = skuRoot;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
