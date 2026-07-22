package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "billing_slas")
public class BillingSla extends TenantScopedEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "storage_mode", nullable = false)
    private String storageMode = "PALLET_POSITION";

    @Column(name = "rate_per_unit", nullable = false)
    private BigDecimal ratePerUnit = BigDecimal.ZERO;

    @Column(name = "pick_fee_per_item", nullable = false)
    private BigDecimal pickFeePerItem = BigDecimal.ZERO;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(String storageMode) {
        this.storageMode = storageMode;
    }

    public BigDecimal getRatePerUnit() {
        return ratePerUnit;
    }

    public void setRatePerUnit(BigDecimal ratePerUnit) {
        this.ratePerUnit = ratePerUnit;
    }

    public BigDecimal getPickFeePerItem() {
        return pickFeePerItem;
    }

    public void setPickFeePerItem(BigDecimal pickFeePerItem) {
        this.pickFeePerItem = pickFeePerItem;
    }
}
