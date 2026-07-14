package com.invsys.api;

import com.invsys.domain.WebhookEvent;
import com.invsys.integration.outbox.ChannelOrderWebhookHandler;
import com.invsys.integration.shopify.ShopifyWebhookValidator;
import com.invsys.integration.webhooks.EasyPostWebhookValidator;
import com.invsys.service.PublicWebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/webhooks")
public class PublicWebhookController {

    private static final Set<String> SUPPORTED_CHANNELS = Set.of("shopify");

    private final PublicWebhookService publicWebhookService;
    private final ChannelOrderWebhookHandler channelOrderWebhookHandler;
    private final ShopifyWebhookValidator shopifyWebhookValidator;
    private final EasyPostWebhookValidator easyPostWebhookValidator;
    private final String shopifyWebhookSecret;
    private final String easyPostWebhookSecret;

    public PublicWebhookController(PublicWebhookService publicWebhookService,
                                   ChannelOrderWebhookHandler channelOrderWebhookHandler,
                                   ShopifyWebhookValidator shopifyWebhookValidator,
                                   EasyPostWebhookValidator easyPostWebhookValidator,
                                   @Value("${invsys.webhooks.shopify-secret}") String shopifyWebhookSecret,
                                   @Value("${invsys.webhooks.easypost-secret}") String easyPostWebhookSecret) {
        this.publicWebhookService = publicWebhookService;
        this.channelOrderWebhookHandler = channelOrderWebhookHandler;
        this.shopifyWebhookValidator = shopifyWebhookValidator;
        this.easyPostWebhookValidator = easyPostWebhookValidator;
        this.shopifyWebhookSecret = shopifyWebhookSecret;
        this.easyPostWebhookSecret = easyPostWebhookSecret;
    }

    @PostMapping("/channels/{platform}")
    public ResponseEntity<Map<String, String>> channelWebhook(
            @PathVariable String platform,
            @RequestHeader(value = "X-Shopify-Shop-Domain", required = false) String shopDomain,
            @RequestHeader(value = "X-Shopify-Topic", required = false) String topic,
            @RequestHeader(value = "X-Shopify-Webhook-Id", required = false) String webhookId,
            @RequestHeader(value = "X-Shopify-Hmac-Sha256", required = false) String shopifyHmac,
            @RequestBody String rawBody) {
        String normalized = platform == null ? "" : platform.toLowerCase();
        if (!SUPPORTED_CHANNELS.contains(normalized)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "unsupported_or_unsigned_platform"));
        }
        if ("shopify".equals(normalized)
                && !shopifyWebhookValidator.isValid(rawBody, shopifyHmac, shopifyWebhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "invalid_signature"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = com.invsys.common.JsonMaps.parse(rawBody);
        String shop = shopDomain != null ? shopDomain : payload.getOrDefault("shop_domain", "unknown").toString();
        String eventId = webhookId != null ? webhookId : UUID.randomUUID().toString();
        Map<String, Object> enriched = new LinkedHashMap<>(payload);
        if (topic != null) {
            enriched.put("topic", topic);
        }
        WebhookEvent event = publicWebhookService.ingestChannel(platform, shop, eventId, enriched);
        channelOrderWebhookHandler.processAsync(event.getId());
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }

    @PostMapping("/easypost")
    public ResponseEntity<Map<String, String>> easyPostWebhook(
            @RequestHeader(value = "X-Hmac-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        if (!easyPostWebhookValidator.isValid(rawBody, signature, easyPostWebhookSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "invalid_signature"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = com.invsys.common.JsonMaps.parse(rawBody);
        Object id = payload.get("id");
        if (id == null || id.toString().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "missing_event_id"));
        }
        publicWebhookService.ingestEasyPost(id.toString(), payload);
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }
}
