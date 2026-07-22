package com.invsys;

import com.invsys.core.tenancy.BootstrapJdbc;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlatformSubscriptionWebhookTest extends AbstractIntegrationTest {

    private static final String STRIPE_SECRET = "whsec_mock_secret";

    @Autowired MockMvc mockMvc;
    @Autowired BootstrapJdbc bootstrapJdbc;
    @Autowired TestDataHelper testDataHelper;
    @Autowired @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource;

    @Test
    void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/v1/public/webhooks/stripe-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=deadbeef")
                        .content("{\"id\":\"evt_bad\",\"type\":\"customer.subscription.updated\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscriptionUpdatedMapsPastDueViaAppOwner() throws Exception {
        JdbcTemplate owner = new JdbcTemplate(bootstrapDataSource);
        UUID tenantId = testDataHelper.createTenant("Sub Past Due", "sub-past-due-" + UUID.randomUUID().toString().substring(0, 8));
        String customerId = "cus_test_" + UUID.randomUUID().toString().substring(0, 8);
        owner.update(
                "UPDATE tenants SET stripe_customer_id = ?, subscription_status = 'ACTIVE' WHERE id = ?",
                customerId, tenantId);

        String body = """
                {
                  "id": "evt_sub_%s",
                  "type": "customer.subscription.updated",
                  "data": {
                    "object": {
                      "id": "sub_123",
                      "customer": "%s",
                      "status": "past_due"
                    }
                  }
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8), customerId);
        long t = System.currentTimeMillis() / 1000;

        mockMvc.perform(post("/api/v1/public/webhooks/stripe-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=" + t + ",v1=" + stripeHmac(t, body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        assertThat(pollSubscriptionStatus(owner, tenantId, "PAST_DUE")).isTrue();
    }

    @Test
    void subscriptionDeletedSuspendsTenant() throws Exception {
        JdbcTemplate owner = new JdbcTemplate(bootstrapDataSource);
        UUID tenantId = testDataHelper.createTenant("Sub Delete", "sub-del-" + UUID.randomUUID().toString().substring(0, 8));
        String customerId = "cus_del_" + UUID.randomUUID().toString().substring(0, 8);
        owner.update(
                "UPDATE tenants SET stripe_customer_id = ?, subscription_status = 'ACTIVE' WHERE id = ?",
                customerId, tenantId);

        String body = """
                {
                  "id": "evt_del_%s",
                  "type": "customer.subscription.deleted",
                  "data": {
                    "object": {
                      "id": "sub_del",
                      "customer": "%s",
                      "status": "canceled"
                    }
                  }
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8), customerId);
        long t = System.currentTimeMillis() / 1000;

        mockMvc.perform(post("/api/v1/public/webhooks/stripe-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=" + t + ",v1=" + stripeHmac(t, body))
                        .content(body))
                .andExpect(status().isOk());

        assertThat(pollSubscriptionStatus(owner, tenantId, "SUSPENDED")).isTrue();
        bootstrapJdbc.updateTenantSubscriptionStatus(tenantId, "ACTIVE");
    }

    private static boolean pollSubscriptionStatus(JdbcTemplate owner, UUID tenantId, String expected)
            throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            String status = owner.queryForObject(
                    "SELECT subscription_status FROM tenants WHERE id = ?", String.class, tenantId);
            if (expected.equals(status)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static String stripeHmac(long timestamp, String body) throws Exception {
        byte[] key;
        if (STRIPE_SECRET.startsWith("whsec_")) {
            String encoded = STRIPE_SECRET.substring("whsec_".length());
            try {
                key = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException ex) {
                key = STRIPE_SECRET.getBytes(StandardCharsets.UTF_8);
            }
        } else {
            key = STRIPE_SECRET.getBytes(StandardCharsets.UTF_8);
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
}
