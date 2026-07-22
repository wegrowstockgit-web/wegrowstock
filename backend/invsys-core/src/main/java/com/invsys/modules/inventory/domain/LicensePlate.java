package com.invsys.modules.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "license_plates")
public class LicensePlate extends TenantScopedEntity {

    @Column(name = "lpn_barcode", nullable = false, length = 50)
    private String lpnBarcode;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    public String getLpnBarcode() {
        return lpnBarcode;
    }

    public void setLpnBarcode(String lpnBarcode) {
        this.lpnBarcode = lpnBarcode;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
