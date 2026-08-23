package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "return_lines")
public class ReturnLine extends TenantScopedEntity {

    @Column(name = "return_id", nullable = false)
    private UUID returnId;

    @Column(name = "sales_order_line_id", nullable = false)
    private UUID salesOrderLineId;

    @Column(name = "quantity_expected", nullable = false)
    private BigDecimal quantityExpected;

    @Column(name = "quantity_received", nullable = false)
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    private String disposition;

    @Column(name = "reason_code")
    private String reasonCode;

    @Column(name = "media_object_id")
    private UUID mediaObjectId;

    @Column(name = "restock_location_id")
    private UUID restockLocationId;

    @Column(name = "restocking_fee_pct")
    private BigDecimal restockingFeePct = BigDecimal.ZERO;

    public UUID getReturnId() {
        return returnId;
    }

    public void setReturnId(UUID returnId) {
        this.returnId = returnId;
    }

    public UUID getSalesOrderLineId() {
        return salesOrderLineId;
    }

    public void setSalesOrderLineId(UUID salesOrderLineId) {
        this.salesOrderLineId = salesOrderLineId;
    }

    public BigDecimal getQuantityExpected() {
        return quantityExpected;
    }

    public void setQuantityExpected(BigDecimal quantityExpected) {
        this.quantityExpected = quantityExpected;
    }

    public BigDecimal getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(BigDecimal quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = disposition;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public UUID getMediaObjectId() {
        return mediaObjectId;
    }

    public void setMediaObjectId(UUID mediaObjectId) {
        this.mediaObjectId = mediaObjectId;
    }

    public UUID getRestockLocationId() {
        return restockLocationId;
    }

    public void setRestockLocationId(UUID restockLocationId) {
        this.restockLocationId = restockLocationId;
    }

    public BigDecimal getRestockingFeePct() {
        return restockingFeePct;
    }

    public void setRestockingFeePct(BigDecimal restockingFeePct) {
        this.restockingFeePct = restockingFeePct != null ? restockingFeePct : BigDecimal.ZERO;
    }
}
