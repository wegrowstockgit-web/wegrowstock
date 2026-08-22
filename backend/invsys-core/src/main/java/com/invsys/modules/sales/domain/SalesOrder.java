package com.invsys.modules.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_policy", nullable = false)
    private AllocationPolicy allocationPolicy = AllocationPolicy.ALLOW_PARTIAL;

    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;

    @Column(name = "manual_discount_total", nullable = false)
    private BigDecimal manualDiscountTotal = BigDecimal.ZERO;

    @Column(name = "quote_notes")
    private String quoteNotes;

    @Column(name = "credit_hold_override", nullable = false)
    private boolean creditHoldOverride = false;

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

    public AllocationPolicy getAllocationPolicy() {
        return allocationPolicy;
    }

    public void setAllocationPolicy(AllocationPolicy allocationPolicy) {
        this.allocationPolicy = allocationPolicy != null ? allocationPolicy : AllocationPolicy.ALLOW_PARTIAL;
    }

    public Instant getQuoteExpiresAt() {
        return quoteExpiresAt;
    }

    public void setQuoteExpiresAt(Instant quoteExpiresAt) {
        this.quoteExpiresAt = quoteExpiresAt;
    }

    public BigDecimal getManualDiscountTotal() {
        return manualDiscountTotal;
    }

    public void setManualDiscountTotal(BigDecimal manualDiscountTotal) {
        this.manualDiscountTotal = manualDiscountTotal != null ? manualDiscountTotal : BigDecimal.ZERO;
    }

    public String getQuoteNotes() {
        return quoteNotes;
    }

    public void setQuoteNotes(String quoteNotes) {
        this.quoteNotes = quoteNotes;
    }

    public boolean isCreditHoldOverride() {
        return creditHoldOverride;
    }

    public void setCreditHoldOverride(boolean creditHoldOverride) {
        this.creditHoldOverride = creditHoldOverride;
    }
}
