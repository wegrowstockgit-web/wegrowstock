package com.invsys.modules.fintech.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "factored_invoices")
public class FactoredInvoice extends TenantScopedEntity {

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "advance_rate", nullable = false)
    private BigDecimal advanceRate = new BigDecimal("85.00");

    @Column(name = "discount_fee_percent", nullable = false)
    private BigDecimal discountFeePercent = new BigDecimal("2.50");

    @Column(name = "funding_status", nullable = false)
    private String fundingStatus = "ELIGIBLE";

    @Column(name = "escrow_payout_ref")
    private String escrowPayoutRef;

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public BigDecimal getAdvanceRate() {
        return advanceRate;
    }

    public void setAdvanceRate(BigDecimal advanceRate) {
        this.advanceRate = advanceRate;
    }

    public BigDecimal getDiscountFeePercent() {
        return discountFeePercent;
    }

    public void setDiscountFeePercent(BigDecimal discountFeePercent) {
        this.discountFeePercent = discountFeePercent;
    }

    public String getFundingStatus() {
        return fundingStatus;
    }

    public void setFundingStatus(String fundingStatus) {
        this.fundingStatus = fundingStatus;
    }

    public String getEscrowPayoutRef() {
        return escrowPayoutRef;
    }

    public void setEscrowPayoutRef(String escrowPayoutRef) {
        this.escrowPayoutRef = escrowPayoutRef;
    }
}
