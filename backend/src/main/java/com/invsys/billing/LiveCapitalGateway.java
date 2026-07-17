package com.invsys.billing;

import com.invsys.common.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Production capital / factoring adapter placeholder.
 * Plug a Stripe Capital (or equivalent) client here — fails closed until configured.
 */
@Component
@Profile("prod")
public class LiveCapitalGateway implements CapitalGateway {

    @Override
    public DrawdownResult createDrawdown(UUID tenantId, BigDecimal amount) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAPITAL_NOT_CONFIGURED",
                "Live capital drawdown is not configured — wire Stripe Capital (or equivalent) in LiveCapitalGateway");
    }

    @Override
    public FactoringPayoutResult fundFactoring(UUID tenantId, UUID invoiceId, BigDecimal advanceAmount) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAPITAL_NOT_CONFIGURED",
                "Live factoring payout is not configured — wire Stripe Capital (or equivalent) in LiveCapitalGateway");
    }
}
