package com.invsys.billing;

import com.invsys.domain.Invoice;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MockStripeGateway implements StripeGateway {

    @Override
    public PaymentIntentResult createPaymentIntent(Invoice invoice, String connectedAccountId, double feePercent) {
        String externalId = "pi_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        BigDecimal fee = invoice.getTotal()
                .multiply(BigDecimal.valueOf(feePercent))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", externalId);
        payload.put("amount", invoice.getTotal());
        payload.put("currency", invoice.getCurrency());
        payload.put("application_fee_amount", fee);
        payload.put("on_behalf_of", connectedAccountId);
        payload.put("status", "requires_payment_method");
        return new PaymentIntentResult(externalId, payload);
    }

    @Override
    public ConnectOnboardingResult createConnectOnboardingUrl(UUID tenantId, String returnUrl) {
        String accountId = "acct_mock_" + tenantId.toString().replace("-", "").substring(0, 12);
        String url = "https://connect.stripe.com/setup/mock/" + accountId + "?return_url="
                + (returnUrl != null ? returnUrl : "http://localhost:3000/settings");
        return new ConnectOnboardingResult(url, accountId);
    }
}
