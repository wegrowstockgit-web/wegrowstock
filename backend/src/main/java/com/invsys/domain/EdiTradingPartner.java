package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "edi_trading_partners")
public class EdiTradingPartner extends TenantScopedEntity {

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "as2_id", nullable = false)
    private String as2Id;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(UUID supplierId) {
        this.supplierId = supplierId;
    }

    public String getAs2Id() {
        return as2Id;
    }

    public void setAs2Id(String as2Id) {
        this.as2Id = as2Id;
    }
}
