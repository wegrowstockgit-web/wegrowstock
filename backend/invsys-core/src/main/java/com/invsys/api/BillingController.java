package com.invsys.api;

import com.invsys.billing.StripeConnectGateway;
import com.invsys.service.BillingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasAnyRole('OWNER','ADMIN')")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/stripe/onboarding-url")
    public OnboardingUrlResponse stripeOnboardingUrl(@RequestParam(required = false) String returnUrl) {
        StripeConnectGateway.ConnectOnboardingResult result = billingService.onboardingUrl(returnUrl);
        return new OnboardingUrlResponse(result.url(), result.accountId());
    }

    @GetMapping("/stripe/status")
    public BillingService.StripeStatusResponse stripeStatus() {
        return billingService.getStripeStatus();
    }

    @GetMapping("/stripe/refresh")
    public BillingService.StripeStatusResponse refreshStripeStatus() {
        return billingService.refreshStripeStatus();
    }

    public record OnboardingUrlResponse(String url, String accountId) {
    }
}
