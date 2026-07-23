package com.invsys.domain;

import com.invsys.core.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "pallet_manifest_items")
public class PalletManifestItem extends TenantScopedEntity {

    @Column(name = "pallet_id", nullable = false)
    private UUID palletId;

    @Column(name = "lpn_id")
    private UUID lpnId;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    public UUID getPalletId() {
        return palletId;
    }

    public void setPalletId(UUID palletId) {
        this.palletId = palletId;
    }

    public UUID getLpnId() {
        return lpnId;
    }

    public void setLpnId(UUID lpnId) {
        this.lpnId = lpnId;
    }

    public UUID getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(UUID shipmentId) {
        this.shipmentId = shipmentId;
    }
}
