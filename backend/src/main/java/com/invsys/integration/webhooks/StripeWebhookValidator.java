package com.invsys.integration.webhooks;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verifies Stripe {@code Stripe-Signature} headers (t / v1) with constant-time compare.
 */
@Component
public class StripeWebhookValidator {

    private static final long DEFAULT_TOLERANCE_SECONDS = 300;

    public boolean isValid(String rawBody, String signatureHeader, String webhookSecret) {
        return isValid(rawBody, signatureHeader, webhookSecret, DEFAULT_TOLERANCE_SECONDS, System.currentTimeMillis() / 1000);
    }

    public boolean isValid(String rawBody, String signatureHeader, String webhookSecret,
                           long toleranceSeconds, long nowEpochSeconds) {
        if (rawBody == null || signatureHeader == null || webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        Long timestamp = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                try {
                    timestamp = Long.parseLong(kv[1]);
                } catch (NumberFormatException ignored) {
                    return false;
                }
            } else if ("v1".equals(kv[0])) {
                v1 = kv[1];
            }
        }
        if (timestamp == null || v1 == null || v1.isBlank()) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - timestamp) > toleranceSeconds) {
            return false;
        }
        try {
            byte[] key = decodeSecret(webhookSecret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String signedPayload = timestamp + "." + rawBody;
            byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                    computed.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                    v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }

    static byte[] decodeSecret(String webhookSecret) {
        if (webhookSecret.startsWith("whsec_")) {
            String encoded = webhookSecret.substring("whsec_".length());
            try {
                return Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException ex) {
                // Dev/mock secrets that are not valid Base64 after the prefix.
                return webhookSecret.getBytes(StandardCharsets.UTF_8);
            }
        }
        return webhookSecret.getBytes(StandardCharsets.UTF_8);
    }
}
