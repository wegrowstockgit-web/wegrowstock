package com.invsys.service;

import com.invsys.billing.StripeGateway;
import com.invsys.domain.StripeAccount;
import com.invsys.repository.StripeAccountRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService {

    private final StripeGateway stripeGateway;
    private final StripeAccountRepository stripeAccountRepository;
    private final String defaultReturnUrl;

    public BillingService(StripeGateway stripeGateway,
                          StripeAccountRepository stripeAccountRepository,
                          @Value("${invsys.stripe.connect-return-url:http://localhost:3000/settings?stripe=success}") String defaultReturnUrl) {
        this.stripeGateway = stripeGateway;
        this.stripeAccountRepository = stripeAccountRepository;
        this.defaultReturnUrl = defaultReturnUrl;
    }

    @Transactional
    public StripeGateway.ConnectOnboardingResult onboardingUrl(String returnUrl) {
        UUID tenantId = TenantContext.requireTenantId();
        String redirect = returnUrl != null && !returnUrl.isBlank() ? returnUrl : defaultReturnUrl;
        StripeGateway.ConnectOnboardingResult result = stripeGateway.createConnectOnboardingUrl(tenantId, redirect);

        StripeAccount account = stripeAccountRepository.findByTenantId(tenantId).orElseGet(() -> {
            StripeAccount created = new StripeAccount();
            created.setTenantId(tenantId);
            return created;
        });
        account.setConnectedAccountId(result.accountId());
        account.setOnboardingStatus("PENDING");
        stripeAccountRepository.save(account);
        return result;
    }

    public StripeStatusResponse getStripeStatus() {
        UUID tenantId = TenantContext.requireTenantId();
        return stripeAccountRepository.findByTenantId(tenantId)
                .map(account -> new StripeStatusResponse(
                        account.getConnectedAccountId(),
                        account.getOnboardingStatus(),
                        account.getCapabilities()))
                .orElse(new StripeStatusResponse(null, "NOT_CONNECTED", Map.of()));
    }

    @Transactional
    public StripeStatusResponse refreshStripeStatus() {
        UUID tenantId = TenantContext.requireTenantId();
        StripeAccount account = stripeAccountRepository.findByTenantId(tenantId).orElse(null);
        if (account == null) {
            return new StripeStatusResponse(null, "NOT_CONNECTED", Map.of());
        }
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("card_payments", "active");
        capabilities.put("transfers", "active");
        account.setOnboardingStatus("ACTIVE");
        account.setCapabilities(capabilities);
        stripeAccountRepository.save(account);
        return new StripeStatusResponse(account.getConnectedAccountId(), account.getOnboardingStatus(), capabilities);
    }

    public record StripeStatusResponse(
            String connectedAccountId,
            String onboardingStatus,
            Map<String, Object> capabilities
    ) {
    }
}
