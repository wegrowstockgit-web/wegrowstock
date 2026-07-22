package com.invsys.integration.webhooks;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Verifies EasyPost {@code X-Hmac-Signature} headers ({@code hmac-sha256-hex=} or raw hex).
 */
@Component
public class EasyPostWebhookValidator {

    public boolean isValid(String rawBody, String signatureHeader, String sharedSecret) {
        if (rawBody == null || signatureHeader == null || sharedSecret == null || sharedSecret.isBlank()) {
            return false;
        }
        String provided = signatureHeader.trim();
        String prefix = "hmac-sha256-hex=";
        if (provided.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            provided = provided.substring(prefix.length()).trim();
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computed = hex(digest);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.US_ASCII),
                    provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            return false;
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }
}
