package com.invsys.billing;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Local / CI capital stub. Never active under {@code prod}. */
@Component
@Profile({"dev", "test", "docker", "default"})
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
