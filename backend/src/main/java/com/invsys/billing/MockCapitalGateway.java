package com.invsys.billing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockCapitalGateway implements CapitalGateway {

    @Override
    public DrawdownResult createDrawdown(UUID tenantId, BigDecimal amount) {
        return new DrawdownResult("cap_" + tenantId.toString().substring(0, 8) + "_" + System.currentTimeMillis(),
                amount, "FUNDED");
    }

    @Override
    public FactoringPayoutResult fundFactoring(UUID tenantId, UUID invoiceId, BigDecimal advanceAmount) {
        return new FactoringPayoutResult(
                "escrow_" + invoiceId.toString().substring(0, 8),
                advanceAmount,
                "FUNDED");
    }
}
