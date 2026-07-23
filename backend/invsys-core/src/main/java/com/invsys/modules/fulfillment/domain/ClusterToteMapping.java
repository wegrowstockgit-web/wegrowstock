package com.invsys.modules.fulfillment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "cluster_tote_mappings")
public class ClusterToteMapping extends TenantScopedEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "tote_barcode", nullable = false, length = 64)
    private String toteBarcode;

    @Column(name = "sales_order_id", nullable = false)
    private UUID salesOrderId;

    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getToteBarcode() {
        return toteBarcode;
    }

    public void setToteBarcode(String toteBarcode) {
        this.toteBarcode = toteBarcode;
    }

    public UUID getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(UUID salesOrderId) {
        this.salesOrderId = salesOrderId;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }
}
