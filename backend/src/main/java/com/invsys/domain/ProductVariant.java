package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
public class ProductVariant extends TenantScopedEntity {

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String sku;

    private String barcode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "reorder_point", nullable = false)
    private BigDecimal reorderPoint = BigDecimal.ZERO;

    @Column(name = "reorder_qty", nullable = false)
    private BigDecimal reorderQty = BigDecimal.ZERO;

    @Column(name = "avg_cost", nullable = false)
    private BigDecimal avgCost = BigDecimal.ZERO;

    @Column(name = "external_sync_enabled", nullable = false)
    private boolean externalSyncEnabled = true;

    @Column(name = "track_serials", nullable = false)
    private boolean trackSerials = false;

    private BigDecimal weight;

    @Column(name = "weight_unit", nullable = false)
    private String weightUnit = "kg";

    private BigDecimal length;

    private BigDecimal width;

    private BigDecimal height;

    @Column(name = "dim_unit", nullable = false)
    private String dimUnit = "cm";

    @Column(name = "default_supplier_id")
    private UUID defaultSupplierId;

    @Column(name = "supplier_lead_time_days", nullable = false)
    private int supplierLeadTimeDays;

    @Column(name = "default_location_id")
    private UUID defaultLocationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> dims = new LinkedHashMap<>();

    @Column(name = "is_kit", nullable = false)
    private boolean kit;

    public UUID getProductId() {
        return productId;
    }

    public void setProductId(UUID productId) {
        this.productId = productId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getReorderPoint() {
        return reorderPoint;
    }

    public void setReorderPoint(BigDecimal reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    public BigDecimal getReorderQty() {
        return reorderQty;
    }

    public void setReorderQty(BigDecimal reorderQty) {
        this.reorderQty = reorderQty;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public void setAvgCost(BigDecimal avgCost) {
        this.avgCost = avgCost;
    }

    public boolean isExternalSyncEnabled() {
        return externalSyncEnabled;
    }

    public void setExternalSyncEnabled(boolean externalSyncEnabled) {
        this.externalSyncEnabled = externalSyncEnabled;
    }

    public boolean isTrackSerials() {
        return trackSerials;
    }

    public void setTrackSerials(boolean trackSerials) {
        this.trackSerials = trackSerials;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public String getWeightUnit() {
        return weightUnit;
    }

    public void setWeightUnit(String weightUnit) {
        this.weightUnit = weightUnit;
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

    public String getDimUnit() {
        return dimUnit;
    }

    public void setDimUnit(String dimUnit) {
        this.dimUnit = dimUnit;
    }

    public UUID getDefaultSupplierId() {
        return defaultSupplierId;
    }

    public void setDefaultSupplierId(UUID defaultSupplierId) {
        this.defaultSupplierId = defaultSupplierId;
    }

    public int getSupplierLeadTimeDays() {
        return supplierLeadTimeDays;
    }

    public void setSupplierLeadTimeDays(int supplierLeadTimeDays) {
        this.supplierLeadTimeDays = supplierLeadTimeDays;
    }

    public UUID getDefaultLocationId() {
        return defaultLocationId;
    }

    public void setDefaultLocationId(UUID defaultLocationId) {
        this.defaultLocationId = defaultLocationId;
    }

    public Map<String, Object> getDims() {
        return dims;
    }

    public void setDims(Map<String, Object> dims) {
        this.dims = dims != null ? dims : new LinkedHashMap<>();
    }

    public boolean isKit() {
        return kit;
    }

    public void setKit(boolean kit) {
        this.kit = kit;
    }
}
