package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sales_orders")
public class SalesOrder extends TenantScopedEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(nullable = false)
    private String channel = "DIRECT";

    @Column(name = "source_location_id")
    private UUID sourceLocationId;

    @Column(name = "customer_po_number")
    private String customerPoNumber;

    @Column(name = "requested_ship_date")
    private Instant requestedShipDate;

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public UUID getSourceLocationId() {
        return sourceLocationId;
    }

    public void setSourceLocationId(UUID sourceLocationId) {
        this.sourceLocationId = sourceLocationId;
    }

    public String getCustomerPoNumber() {
        return customerPoNumber;
    }

    public void setCustomerPoNumber(String customerPoNumber) {
        this.customerPoNumber = customerPoNumber;
    }

    public Instant getRequestedShipDate() {
        return requestedShipDate;
    }

    public void setRequestedShipDate(Instant requestedShipDate) {
        this.requestedShipDate = requestedShipDate;
    }
}
