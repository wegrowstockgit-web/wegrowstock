package com.invsys.domain;

import com.invsys.core.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "pallet_manifests")
public class PalletManifest extends TenantScopedEntity {

    @Column(name = "sscc_18", nullable = false, length = 20)
    private String sscc18;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "carrier_name", length = 120)
    private String carrierName;

    @Column(nullable = false, length = 32)
    private String status = "BUILDING";

    @Column(name = "bol_number", length = 64)
    private String bolNumber;

    public String getSscc18() {
        return sscc18;
    }

    public void setSscc18(String sscc18) {
        this.sscc18 = sscc18;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getCarrierName() {
        return carrierName;
    }

    public void setCarrierName(String carrierName) {
        this.carrierName = carrierName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBolNumber() {
        return bolNumber;
    }

    public void setBolNumber(String bolNumber) {
        this.bolNumber = bolNumber;
    }
}
