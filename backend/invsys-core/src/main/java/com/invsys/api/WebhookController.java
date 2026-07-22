package com.invsys.api;

import com.invsys.core.common.ApiException;
import com.invsys.core.common.JsonMaps;
import com.invsys.domain.WebhookEvent;
import com.invsys.service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/stripe")
    public WebhookEvent stripe(@RequestHeader(value = "Stripe-Signature", required = false) String signature,
                               @RequestBody String rawBody) {
        Map<String, Object> payload = JsonMaps.parse(rawBody);
        String eventId = payload.getOrDefault("id", UUID.randomUUID().toString()).toString();
        WebhookEvent event = webhookService.ingest("STRIPE", eventId, signature, rawBody);
        if (!event.isSignatureValid()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Invalid Stripe signature");
        }
        return event;
    }
}
