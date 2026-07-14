package com.invsys.billing;

import java.math.BigDecimal;
import java.util.UUID;

public interface CapitalGateway {
    DrawdownResult createDrawdown(UUID tenantId, BigDecimal amount);

    FactoringPayoutResult fundFactoring(UUID tenantId, UUID invoiceId, BigDecimal advanceAmount);

    record DrawdownResult(String referenceId, BigDecimal amount, String status) {
    }

    record FactoringPayoutResult(String escrowPayoutRef, BigDecimal netAdvance, String status) {
    }
}
