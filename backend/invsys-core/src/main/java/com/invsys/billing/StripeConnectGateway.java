package com.invsys.billing;

import com.invsys.modules.sales.domain.Invoice;

import java.util.Map;
import java.util.UUID;

/**
 * Platform Stripe Connect gateway (PaymentIntents + Connect onboarding).
 */
public interface StripeConnectGateway {
    PaymentIntentResult createPaymentIntent(Invoice invoice, String connectedAccountId, double feePercent);

    ConnectOnboardingResult createConnectOnboardingUrl(UUID tenantId, String returnUrl);

    record PaymentIntentResult(String externalId, Map<String, Object> rawPayload) {
    }

    record ConnectOnboardingResult(String url, String accountId) {
    }
}
