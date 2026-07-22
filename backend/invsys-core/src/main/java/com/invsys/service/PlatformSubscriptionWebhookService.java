package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.common.JsonMaps;
import com.invsys.integration.webhooks.StripeWebhookValidator;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Platform SaaS subscription lifecycle webhooks (customer.subscription.*).
 * Updates {@code tenants.subscription_status} via app_owner so SUSPENDED tenants remain writable.
 */
@Service
public class PlatformSubscriptionWebhookService {

    private static final Logger log = LoggerFactory.getLogger(PlatformSubscriptionWebhookService.class);
    private static final Set<String> HANDLED = Set.of(
            "customer.subscription.updated",
            "customer.subscription.deleted"
    );

    private final StripeWebhookValidator stripeWebhookValidator;
    private final BootstrapJdbc bootstrapJdbc;
    private final String platformWebhookSecret;
    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();

    public PlatformSubscriptionWebhookService(
            StripeWebhookValidator stripeWebhookValidator,
            BootstrapJdbc bootstrapJdbc,
            @Value("${invsys.stripe.platform-webhook-secret}") String platformWebhookSecret) {
        this.stripeWebhookValidator = stripeWebhookValidator;
        this.bootstrapJdbc = bootstrapJdbc;
        this.platformWebhookSecret = platformWebhookSecret;
    }

    public Map<String, String> accept(String signatureHeader, String rawBody) {
        if (!stripeWebhookValidator.isValid(rawBody, signatureHeader, platformWebhookSecret)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Invalid Stripe platform signature");
        }
        Map<String, Object> payload = JsonMaps.parse(rawBody);
        String type = stringVal(payload.get("type"));
        String eventId = stringVal(payload.get("id"));
        if (type == null || !HANDLED.contains(type)) {
            return Map.of("status", "ignored", "eventId", eventId != null ? eventId : "");
        }
        virtualThreads.execute(() -> processEvent(type, payload, eventId));
        return Map.of("status", "accepted", "eventId", eventId != null ? eventId : "");
    }

    void processEvent(String type, Map<String, Object> payload, String eventId) {
        try {
            Map<String, Object> data = asMap(payload.get("data"));
            Map<String, Object> object = data != null ? asMap(data.get("object")) : null;
            if (object == null) {
                log.warn("Platform subscription webhook {} missing data.object", eventId);
                return;
            }
            String customerId = stringVal(object.get("customer"));
            if (customerId == null || customerId.isBlank()) {
                log.warn("Platform subscription webhook {} missing customer id", eventId);
                return;
            }
            Optional<UUID> tenantId = bootstrapJdbc.findTenantIdByStripeCustomerId(customerId);
            if (tenantId.isEmpty()) {
                log.warn("No tenant for Stripe customer {} (event {})", customerId, eventId);
                return;
            }
            String status = mapSubscriptionStatus(type, stringVal(object.get("status")));
            int updated = bootstrapJdbc.updateTenantSubscriptionStatus(tenantId.get(), status);
            log.info("Tenant {} subscription_status → {} (event {}, rows={})",
                    tenantId.get(), status, eventId, updated);
        } catch (Exception ex) {
            log.error("Failed processing platform subscription event {}: {}", eventId, ex.getMessage(), ex);
        }
    }

    static String mapSubscriptionStatus(String eventType, String stripeStatus) {
        if ("customer.subscription.deleted".equals(eventType)) {
            return "SUSPENDED";
        }
        if (stripeStatus == null) {
            return "SUSPENDED";
        }
        return switch (stripeStatus.toLowerCase()) {
            case "active", "trialing" -> "ACTIVE";
            case "past_due" -> "PAST_DUE";
            default -> "SUSPENDED";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static String stringVal(Object value) {
        return value == null ? null : value.toString();
    }
}
