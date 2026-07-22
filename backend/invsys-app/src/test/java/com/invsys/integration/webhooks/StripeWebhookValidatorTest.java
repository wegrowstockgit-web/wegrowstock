package com.invsys.integration.webhooks;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class StripeWebhookValidatorTest {

    private final StripeWebhookValidator validator = new StripeWebhookValidator();

    @Test
    void acceptsMatchingSignatureWithinTolerance() throws Exception {
        String secret = "whsec_mock_secret";
        String body = "{\"id\":\"evt_1\"}";
        long t = 1_700_000_000L;
        String sig = "t=" + t + ",v1=" + hmac(secret, t + "." + body);
        assertThat(validator.isValid(body, sig, secret, 300, t)).isTrue();
    }

    @Test
    void rejectsTamperedBody() throws Exception {
        String secret = "whsec_mock_secret";
        String body = "{\"id\":\"evt_1\"}";
        long t = 1_700_000_000L;
        String sig = "t=" + t + ",v1=" + hmac(secret, t + "." + body);
        assertThat(validator.isValid("{\"id\":\"evt_other\"}", sig, secret, 300, t)).isFalse();
    }

    @Test
    void rejectsStaleTimestamp() throws Exception {
        String secret = "whsec_mock_secret";
        String body = "{\"id\":\"evt_1\"}";
        long t = 1_700_000_000L;
        String sig = "t=" + t + ",v1=" + hmac(secret, t + "." + body);
        assertThat(validator.isValid(body, sig, secret, 300, t + 10_000)).isFalse();
    }

    private static String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
