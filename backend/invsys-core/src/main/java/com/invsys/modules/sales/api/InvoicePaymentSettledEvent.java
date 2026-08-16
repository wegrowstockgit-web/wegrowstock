package com.invsys.modules.sales.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published when an invoice is marked PAID. Fintech applies factoring payback
 * synchronously and records the amount on this event for the outbox payload.
 */
public class InvoicePaymentSettledEvent {

    private final UUID invoiceId;
    private final BigDecimal amount;
    private BigDecimal factoringPayback = BigDecimal.ZERO;

    public InvoicePaymentSettledEvent(UUID invoiceId, BigDecimal amount) {
        this.invoiceId = invoiceId;
        this.amount = amount;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getFactoringPayback() {
        return factoringPayback;
    }

    public void setFactoringPayback(BigDecimal factoringPayback) {
        this.factoringPayback = factoringPayback != null ? factoringPayback : BigDecimal.ZERO;
    }
}
