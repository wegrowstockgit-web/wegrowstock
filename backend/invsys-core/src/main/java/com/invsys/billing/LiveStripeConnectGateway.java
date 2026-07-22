package com.invsys.billing;

import com.invsys.modules.sales.domain.Invoice;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.PaymentIntent;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Live Stripe Connect SDK gateway. Requires {@code STRIPE_SECRET_KEY} at startup.
 */
@Component
@Profile("prod")
public class LiveStripeConnectGateway implements StripeConnectGateway, StripeGateway {

    private final String connectReturnUrl;

    public LiveStripeConnectGateway(
            @Value("${invsys.stripe.secret-key:}") String secretKey,
            @Value("${invsys.stripe.connect-return-url:https://app.example.com/settings/billing}") String connectReturnUrl) {
        if (secretKey == null || secretKey.isBlank() || "sk_test_mock".equals(secretKey)) {
            throw new IllegalStateException(
                    "STRIPE_SECRET_KEY (invsys.stripe.secret-key) must be configured for production profile");
        }
        Stripe.apiKey = secretKey;
        this.connectReturnUrl = connectReturnUrl;
    }

    @Override
    public PaymentIntentResult createPaymentIntent(Invoice invoice, String connectedAccountId, double feePercent) {
        try {
            BigDecimal fee = invoice.getTotal()
                    .multiply(BigDecimal.valueOf(feePercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            long amountCents = invoice.getTotal().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            long feeCents = fee.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();

            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(invoice.getCurrency() != null ? invoice.getCurrency().toLowerCase() : "usd")
                    .setApplicationFeeAmount(feeCents)
                    .putMetadata("invoice_id", invoice.getId().toString());
            if (connectedAccountId != null && !connectedAccountId.isBlank()) {
                builder.setOnBehalfOf(connectedAccountId);
                builder.setTransferData(PaymentIntentCreateParams.TransferData.builder()
                        .setDestination(connectedAccountId)
                        .build());
            }
            PaymentIntent intent = PaymentIntent.create(builder.build());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", intent.getId());
            raw.put("status", intent.getStatus());
            raw.put("amount", intent.getAmount());
            raw.put("currency", intent.getCurrency());
            return new PaymentIntentResult(intent.getId(), raw);
        } catch (StripeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe API error: " + ex.getMessage(), ex);
        }
    }

    @Override
    public ConnectOnboardingResult createConnectOnboardingUrl(UUID tenantId, String returnUrl) {
        try {
            Account account = Account.create(AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .putMetadata("tenant_id", tenantId.toString())
                    .build());
            String resolvedReturn = returnUrl != null && !returnUrl.isBlank() ? returnUrl : connectReturnUrl;
            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(account.getId())
                    .setRefreshUrl(resolvedReturn)
                    .setReturnUrl(resolvedReturn)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build());
            return new ConnectOnboardingResult(link.getUrl(), account.getId());
        } catch (StripeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Stripe Connect onboarding failed: " + ex.getMessage(), ex);
        }
    }
}
