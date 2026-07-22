package com.invsys.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.invsys.core.common.TenantScopedEntity;

@Entity
@Table(name = "payments")
public class Payment extends TenantScopedEntity {

    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "fee_amount", nullable = false)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "balance_txn_ref")
    private String balanceTxnRef;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt = Instant.now();

    public UUID getPaymentIntentId() {
        return paymentIntentId;
    }

    public void setPaymentIntentId(UUID paymentIntentId) {
        this.paymentIntentId = paymentIntentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
    }

    public String getBalanceTxnRef() {
        return balanceTxnRef;
    }

    public void setBalanceTxnRef(String balanceTxnRef) {
        this.balanceTxnRef = balanceTxnRef;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }
}
