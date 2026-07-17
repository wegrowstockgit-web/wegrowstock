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
@Table(name = "inventory_ledger")
public class InventoryLedger extends TenantScopedEntity {

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "lot_id")
    private UUID lotId;

    @Column(name = "movement_type", nullable = false)
    private String movementType;

    @Column(name = "quantity_delta", nullable = false)
    private BigDecimal quantityDelta;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "transfer_group_id")
    private UUID transferGroupId;

    @Column(name = "reversal_of_ledger_id")
    private UUID reversalOfLedgerId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "landed_cost_component", nullable = false)
    private BigDecimal landedCostComponent = BigDecimal.ZERO;

    @Column(name = "serial_number_id")
    private UUID serialNumberId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "owner_customer_id")
    private UUID ownerCustomerId;

    public UUID getVariantId() {
        return variantId;
    }

    public void setVariantId(UUID variantId) {
        this.variantId = variantId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public void setQuantityDelta(BigDecimal quantityDelta) {
        this.quantityDelta = quantityDelta;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }

    public UUID getTransferGroupId() {
        return transferGroupId;
    }

    public void setTransferGroupId(UUID transferGroupId) {
        this.transferGroupId = transferGroupId;
    }

    public UUID getReversalOfLedgerId() {
        return reversalOfLedgerId;
    }

    public void setReversalOfLedgerId(UUID reversalOfLedgerId) {
        this.reversalOfLedgerId = reversalOfLedgerId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getLandedCostComponent() {
        return landedCostComponent;
    }

    public void setLandedCostComponent(BigDecimal landedCostComponent) {
        this.landedCostComponent = landedCostComponent != null ? landedCostComponent : BigDecimal.ZERO;
    }

    public UUID getSerialNumberId() {
        return serialNumberId;
    }

    public void setSerialNumberId(UUID serialNumberId) {
        this.serialNumberId = serialNumberId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }

    public UUID getOwnerCustomerId() {
        return ownerCustomerId;
    }

    public void setOwnerCustomerId(UUID ownerCustomerId) {
        this.ownerCustomerId = ownerCustomerId;
    }
}
