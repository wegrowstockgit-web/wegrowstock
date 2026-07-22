package com.invsys.integration.webhooks;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import com.invsys.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WebhookReplayDriftFilterTest extends AbstractIntegrationTest {

    private static final String SHOPIFY_SECRET = "shopify_mock_secret";
    private static final String STRIPE_SECRET = "whsec_mock_secret";

    @Autowired MockMvc mockMvc;

    @Test
    void parseStripeAndShopifyTimestamps() {
        assertThat(WebhookReplayDriftFilter.parseStripeTimestamp("t=1710000000,v1=abc"))
                .isEqualTo(1710000000L);
        assertThat(WebhookReplayDriftFilter.parseShopifyTimestamp("1710000000"))
                .isEqualTo(1710000000L);
        assertThat(WebhookReplayDriftFilter.parseShopifyTimestamp("2024-03-09T12:00:00Z"))
                .isEqualTo(Instant.parse("2024-03-09T12:00:00Z").getEpochSecond());
    }

    @Test
    void stripeRejectsStaleSignatureTimestamp() throws Exception {
        String body = "{\"id\":\"evt_stale\"}";
        long stale = Instant.now().getEpochSecond() - 600;
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=" + stale + ",v1=" + stripeHmac(stale, body))
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shopifyRejectsMissingTriggeredAt() throws Exception {
        String body = "{\"shop_domain\":\"demo.myshopify.com\"}";
        mockMvc.perform(post("/api/v1/public/webhooks/channels/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", shopifyHmac(body))
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shopifyRejectsStaleTriggeredAt() throws Exception {
        String body = "{\"shop_domain\":\"demo.myshopify.com\"}";
        long stale = Instant.now().getEpochSecond() - 900;
        mockMvc.perform(post("/api/v1/public/webhooks/channels/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", shopifyHmac(body))
                        .header("X-Shopify-Triggered-At", String.valueOf(stale))
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shopifyAcceptsFreshTriggeredAt() throws Exception {
        String body = "{\"shop_domain\":\"demo.myshopify.com\"}";
        long now = Instant.now().getEpochSecond();
        mockMvc.perform(post("/api/v1/public/webhooks/channels/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", shopifyHmac(body))
                        .header("X-Shopify-Triggered-At", String.valueOf(now))
                        .content(body))
                .andExpect(status().isOk());
    }

    private static String shopifyHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SHOPIFY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String stripeHmac(long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(STRIPE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signedPayload = timestamp + "." + body;
        return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
    }
}
