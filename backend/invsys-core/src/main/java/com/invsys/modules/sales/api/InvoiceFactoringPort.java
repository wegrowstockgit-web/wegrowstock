package com.invsys.modules.sales.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Sales-owned port for invoice factoring. Fintech implements this so sales
 * never imports fintech {@code service} / {@code repository} types.
 */
public interface InvoiceFactoringPort {

    Optional<String> fundingStatus(UUID tenantId, UUID invoiceId);

    FactoringResult requestFactoring(UUID invoiceId);

    record FactoringResult(
            UUID id,
            UUID invoiceId,
            BigDecimal advanceRate,
            BigDecimal discountFeePercent,
            String fundingStatus,
            String escrowPayoutRef
    ) {
    }
}
