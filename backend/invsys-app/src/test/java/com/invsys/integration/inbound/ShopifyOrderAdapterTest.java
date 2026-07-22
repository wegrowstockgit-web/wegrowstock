package com.invsys.integration.inbound;

import com.invsys.core.common.ApiException;
import com.invsys.domain.IntegrationChannel;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.integration.channel.IntegrationChannelStatus;
import com.invsys.integration.channel.IntegrationChannelType;
import com.invsys.integration.shopify.ShopifyWebhookValidator;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.service.IntegrationChannelService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShopifyOrderAdapterTest {

    @Mock ProductVariantRepository variantRepository;
    @Mock IntegrationChannelService channelService;

    private ShopifyOrderAdapter adapter;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new ShopifyOrderAdapter(
                new ObjectMapper(),
                variantRepository,
                channelService,
                new ShopifyWebhookValidator());
        TenantContext.setTenantId(tenantId);
        when(channelService.findActive(IntegrationChannelType.SHOPIFY)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void supportsShopifyOnly() {
        assertThat(adapter.supports("SHOPIFY")).isTrue();
        assertThat(adapter.supports("shopify")).isTrue();
        assertThat(adapter.supports("EDI")).isFalse();
    }

    @Test
    void translateMapsAddressesAndLines() {
        when(variantRepository.findByTenantIdAndSku(eq(tenantId), eq("SKU-1")))
                .thenReturn(Optional.of(new ProductVariant()));

        String json = """
                {
                  "order": {
                    "name": "#42",
                    "email": "a@b.com",
                    "billing_address": {"address1":"1 A","city":"Austin","province":"TX","zip":"78701","country":"US","name":"Bill"},
                    "shipping_address": {"address1":"2 B","city":"Dallas","province":"TX","zip":"75201","country":"US","first_name":"Ship","last_name":"To"},
                    "line_items": [{"sku":"SKU-1","quantity":2,"price":"9.50"}]
                  }
                }
                """;

        CanonicalInboundOrder order = adapter.translate(json, Map.of());
        assertThat(order.channelSource()).isEqualTo(ChannelSource.SHOPIFY);
        assertThat(order.externalOrderRef()).isEqualTo("#42");
        assertThat(order.customerIdentifier()).isEqualTo("a@b.com");
        assertThat(order.billingAddress().city()).isEqualTo("Austin");
        assertThat(order.shippingAddress().name()).isEqualTo("Ship To");
        assertThat(order.lines()).hasSize(1);
        assertThat(order.lines().getFirst().sku()).isEqualTo("SKU-1");
        assertThat(order.lines().getFirst().quantity()).isEqualByComparingTo("2");
        assertThat(order.lines().getFirst().unitPrice()).isEqualByComparingTo(new BigDecimal("9.50"));
    }

    @Test
    void translateRejectsBlankAndInvalidJson() {
        assertThatThrownBy(() -> adapter.translate(" ", Map.of()))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapter.translate("{not-json", Map.of()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void verifyHmacAgainstVaultedWebhookSecret() throws Exception {
        String secret = "shopify-hub-secret";
        String json = "{\"name\":\"#99\",\"line_items\":[{\"sku\":\"SKU-1\",\"quantity\":1,\"price\":\"1\"}]}";
        String hmac = hmacBase64(secret, json);

        IntegrationChannel channel = new IntegrationChannel();
        channel.setChannelType(IntegrationChannelType.SHOPIFY);
        channel.setStatus(IntegrationChannelStatus.ACTIVE);
        channel.setEncryptedCredentials(new byte[]{1, 2, 3});

        when(channelService.findActive(IntegrationChannelType.SHOPIFY)).thenReturn(Optional.of(channel));
        when(channelService.decryptCredentials(channel)).thenReturn(Map.of("webhookSecret", secret));
        when(variantRepository.findByTenantIdAndSku(eq(tenantId), eq("SKU-1")))
                .thenReturn(Optional.of(new ProductVariant()));

        CanonicalInboundOrder order = adapter.translate(json, Map.of(ShopifyOrderAdapter.HEADER_HMAC, hmac));
        assertThat(order.externalOrderRef()).isEqualTo("#99");

        assertThatThrownBy(() -> adapter.translate(json, Map.of(ShopifyOrderAdapter.HEADER_HMAC, "bad")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid");

        assertThatThrownBy(() -> adapter.translate(json, Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("required");
    }

    private static String hmacBase64(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
