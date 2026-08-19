package com.invsys.integration.shopify;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class ShopifyWebhookValidator {

    public boolean isValid(String rawBody, String hmacHeader, String sharedSecret) {
        if (hmacHeader == null || sharedSecret == null || rawBody == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] provided;
            try {
                provided = Base64.getDecoder().decode(hmacHeader.trim());
            } catch (IllegalArgumentException ex) {
                return false;
            }
            return MessageDigest.isEqual(digest, provided);
        } catch (Exception ex) {
            return false;
        }
    }
}
