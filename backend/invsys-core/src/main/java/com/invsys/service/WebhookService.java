package com.invsys.service;

import com.invsys.core.common.JsonMaps;
import com.invsys.domain.WebhookEvent;
import com.invsys.integration.alerts.IntegrationFailurePublisher;
import com.invsys.integration.webhooks.StripeWebhookValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    private final WebhookInboxWriter webhookInboxWriter;
    private final WebhookEventProcessor webhookEventProcessor;
    private final StripeWebhookValidator stripeWebhookValidator;
    private final IntegrationFailurePublisher failurePublisher;
    private final String webhookSecret;

    public WebhookService(WebhookInboxWriter webhookInboxWriter,
                          WebhookEventProcessor webhookEventProcessor,
                          StripeWebhookValidator stripeWebhookValidator,
                          IntegrationFailurePublisher failurePublisher,
                          @Value("${invsys.stripe.webhook-secret}") String webhookSecret) {
        this.webhookInboxWriter = webhookInboxWriter;
        this.webhookEventProcessor = webhookEventProcessor;
        this.stripeWebhookValidator = stripeWebhookValidator;
        this.failurePublisher = failurePublisher;
        this.webhookSecret = webhookSecret;
    }

    public WebhookEvent ingest(String source, String externalEventId, String signature, String rawBody) {
        Map<String, Object> payload = JsonMaps.parse(rawBody);
        UUID tenantId = null;
        if (payload.get("tenant_id") != null) {
            tenantId = UUID.fromString(payload.get("tenant_id").toString());
        }
        boolean signatureValid = stripeWebhookValidator.isValid(rawBody, signature, webhookSecret);
        WebhookEvent event = webhookInboxWriter.insertIfAbsent(
                source, externalEventId, signatureValid, payload, tenantId);
        if (!signatureValid && tenantId != null) {
            failurePublisher.publish(tenantId, "STRIPE", "WEBHOOK_SIGNATURE_FAILED",
                    "Invalid Stripe-Signature for event " + externalEventId, event.getId());
        }
        if (signatureValid && event.getProcessedAt() == null && event.getError() == null) {
            webhookEventProcessor.processAsync(event.getId());
        }
        return event;
    }
}
