package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "returns")
public class ReturnOrder extends TenantScopedEntity {

    @Column(name = "sales_order_id", nullable = false)
    private UUID salesOrderId;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String status = "REQUESTED";

    public UUID getSalesOrderId() {
        return salesOrderId;
    }

    public void setSalesOrderId(UUID salesOrderId) {
        this.salesOrderId = salesOrderId;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
