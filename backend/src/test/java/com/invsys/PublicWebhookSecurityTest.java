package com.invsys;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PublicWebhookSecurityTest extends AbstractIntegrationTest {

    private static final String SHOPIFY_SECRET = "shopify_mock_secret";
    private static final String EASYPOST_SECRET = "easypost_mock_secret";
    private static final String STRIPE_SECRET = "whsec_mock_secret";

    @Autowired MockMvc mockMvc;

    @Test
    void easyPostRejectsMissingSignature() throws Exception {
        mockMvc.perform(post("/api/v1/public/webhooks/easypost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt-test-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void easyPostAcceptsValidHmac() throws Exception {
        String body = "{\"id\":\"evt-test-valid\"}";
        mockMvc.perform(post("/api/v1/public/webhooks/easypost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hmac-Signature", "hmac-sha256-hex=" + easyPostHmac(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void unsupportedChannelRejectedWithoutValidator() throws Exception {
        mockMvc.perform(post("/api/v1/public/webhooks/channels/amazon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shop_domain\":\"demo.amazon\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shopifyAcceptsValidHmac() throws Exception {
        String shopifyBody = "{\"shop_domain\":\"demo.myshopify.com\"}";
        mockMvc.perform(post("/api/v1/public/webhooks/channels/shopify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Shopify-Hmac-Sha256", shopifyHmac(shopifyBody))
                        .content(shopifyBody))
                .andExpect(status().isOk());
    }

    @Test
    void stripeRejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .content("{\"id\":\"evt_bad\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stripeAcceptsValidSignature() throws Exception {
        String body = "{\"id\":\"evt_ok\",\"type\":\"checkout.session.completed\"}";
        long t = System.currentTimeMillis() / 1000;
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=" + t + ",v1=" + stripeHmac(t, body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void protectedRoutesReturn401Not403WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    private static String shopifyHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SHOPIFY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String easyPostHmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(EASYPOST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String stripeHmac(long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(STRIPE_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signedPayload = timestamp + "." + body;
        return HexFormat.of().formatHex(mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
    }
}
