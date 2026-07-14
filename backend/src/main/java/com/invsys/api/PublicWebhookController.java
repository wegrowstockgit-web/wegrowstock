package com.invsys.api;

import com.invsys.common.ApiException;
import com.invsys.domain.WebhookEvent;
import com.invsys.integration.outbox.ChannelOrderWebhookHandler;
import com.invsys.integration.shopify.ShopifyWebhookValidator;
import com.invsys.integration.webhooks.EasyPostWebhookValidator;
import com.invsys.service.AccountingPaymentWebhookService;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
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
    private final AccountingPaymentWebhookService accountingPaymentWebhookService;
    private final String shopifyWebhookSecret;
    private final String easyPostWebhookSecret;
    private final String accountingWebhookSecret;

    public PublicWebhookController(PublicWebhookService publicWebhookService,
                                   ChannelOrderWebhookHandler channelOrderWebhookHandler,
                                   ShopifyWebhookValidator shopifyWebhookValidator,
                                   EasyPostWebhookValidator easyPostWebhookValidator,
                                   AccountingPaymentWebhookService accountingPaymentWebhookService,
                                   @Value("${invsys.webhooks.shopify-secret}") String shopifyWebhookSecret,
                                   @Value("${invsys.webhooks.easypost-secret}") String easyPostWebhookSecret,
                                   @Value("${invsys.webhooks.accounting-secret:accounting_mock_secret}") String accountingWebhookSecret) {
        this.publicWebhookService = publicWebhookService;
        this.channelOrderWebhookHandler = channelOrderWebhookHandler;
        this.shopifyWebhookValidator = shopifyWebhookValidator;
        this.easyPostWebhookValidator = easyPostWebhookValidator;
        this.accountingPaymentWebhookService = accountingPaymentWebhookService;
        this.shopifyWebhookSecret = shopifyWebhookSecret;
        this.easyPostWebhookSecret = easyPostWebhookSecret;
        this.accountingWebhookSecret = accountingWebhookSecret;
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

    @PostMapping("/accounting/{provider}")
    public ResponseEntity<Map<String, String>> accountingWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Accounting-Signature", required = false) String signature,
            @RequestBody String rawBody) {
        if (!isValidAccountingSignature(rawBody, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Invalid accounting webhook signature");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = com.invsys.common.JsonMaps.parse(rawBody);
        return ResponseEntity.ok(accountingPaymentWebhookService.handlePayment(provider, payload));
    }

    private boolean isValidAccountingSignature(String rawBody, String signatureHeader) {
        if (rawBody == null || signatureHeader == null || accountingWebhookSecret == null
                || accountingWebhookSecret.isBlank()) {
            return false;
        }
        String provided = signatureHeader.trim();
        if (provided.equals(accountingWebhookSecret)) {
            return true;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(accountingWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.US_ASCII),
                    provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }
}
