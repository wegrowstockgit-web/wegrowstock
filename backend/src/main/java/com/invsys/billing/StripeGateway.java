package com.invsys.billing;

import com.invsys.domain.Invoice;

import java.util.Map;
import java.util.UUID;

public interface StripeGateway {
    PaymentIntentResult createPaymentIntent(Invoice invoice, String connectedAccountId, double feePercent);

    ConnectOnboardingResult createConnectOnboardingUrl(UUID tenantId, String returnUrl);

    record PaymentIntentResult(String externalId, Map<String, Object> rawPayload) {
    }

    record ConnectOnboardingResult(String url, String accountId) {
    }
}
