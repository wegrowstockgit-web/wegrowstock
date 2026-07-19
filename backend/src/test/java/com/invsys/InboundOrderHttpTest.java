package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Customer;
import com.invsys.domain.EdiTradingPartner;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.SoftKitComponent;
import com.invsys.api.dto.IntegrationChannelUpsertRequest;
import com.invsys.integration.channel.SyncDirection;
import com.invsys.integration.inbound.ShopifyOrderAdapter;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.EdiTradingPartnerRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.SoftKitComponentRepository;
import com.invsys.service.IntegrationChannelService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InboundOrderHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SoftKitComponentRepository softKitComponentRepository;
    @Autowired EdiTradingPartnerRepository ediTradingPartnerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired IntegrationSyncLogRepository syncLogRepository;
    @Autowired IntegrationChannelService channelService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shopifyInboundCreatesOrderAndExplodesSoftKit() throws Exception {
        String slug = "inb-s-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Inbound Shopify", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Shopify Buyer");
        customer.setEmail("buyer@" + slug + ".test");
        customerRepository.save(customer);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("INB");
        product.setName("Inbound Kit");
        product = productRepository.save(product);

        ProductVariant kit = new ProductVariant();
        kit.setTenantId(tenantId);
        kit.setProductId(product.getId());
        kit.setSku("BUNDLE-INB");
        kit.setSoftKit(true);
        final ProductVariant savedKit = variantRepository.save(kit);

        ProductVariant a = variant(tenantId, product.getId(), "INB-A");
        ProductVariant b = variant(tenantId, product.getId(), "INB-B");

        SoftKitComponent c1 = new SoftKitComponent();
        c1.setTenantId(tenantId);
        c1.setParentKitId(savedKit.getId());
        c1.setComponentId(a.getId());
        c1.setQuantity(new BigDecimal("2"));
        softKitComponentRepository.save(c1);

        SoftKitComponent c2 = new SoftKitComponent();
        c2.setTenantId(tenantId);
        c2.setParentKitId(savedKit.getId());
        c2.setComponentId(b.getId());
        c2.setQuantity(new BigDecimal("1"));
        softKitComponentRepository.save(c2);
        TenantContext.clear();

        String payload = """
                {
                  "topic": "orders/create",
                  "name": "#9001",
                  "email": "buyer@%s.test",
                  "billing_address": {"name":"Bill","address1":"1 Main","city":"Austin","province":"TX","zip":"78701","country":"US"},
                  "shipping_address": {"name":"Ship","address1":"2 Oak","city":"Dallas","province":"TX","zip":"75201","country":"US"},
                  "line_items": [{"sku":"BUNDLE-INB","quantity":3,"price":"29.99"}]
                }
                """.formatted(slug);

        MvcResult result = mockMvc.perform(post("/api/v1/integrations/inbound/SHOPIFY")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("SHOPIFY"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.externalOrderRef").value("#9001"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(body.get("id").asString());

        TenantContext.setTenantId(tenantId);
        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow();
        assertThat(order.getCustomerId()).isEqualTo(customer.getId());
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(orderId);
        assertThat(lines).hasSize(2);
        assertThat(lines).noneMatch(l -> savedKit.getId().equals(l.getVariantId()));
        assertThat(lines).anySatisfy(l -> {
            assertThat(l.getVariantId()).isEqualTo(a.getId());
            assertThat(l.getQtyOrdered()).isEqualByComparingTo("6");
        });
        assertThat(lines).anySatisfy(l -> {
            assertThat(l.getVariantId()).isEqualTo(b.getId());
            assertThat(l.getQtyOrdered()).isEqualByComparingTo("3");
        });
        assertThat(syncLogRepository.findByTenantIdAndSystemOrderByCreatedAtDesc(tenantId, "SHOPIFY"))
                .anySatisfy(log -> {
                    assertThat(log.getDirection()).isEqualTo(SyncDirection.INBOUND);
                    assertThat(log.getEntityType()).isEqualTo("ORDER");
                    assertThat(log.getExternalId()).isEqualTo("#9001");
                    assertThat(log.getStatus()).isEqualTo("SUCCESS");
                    assertThat(log.getEntityId()).isEqualTo(orderId);
                });
    }

    @Test
    void shopifyInboundRejectsInvalidHmacWhenSecretConfigured() throws Exception {
        String slug = "inb-h-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Inbound HMAC", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Buyer");
        customerRepository.save(customer);
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("HM");
        product.setName("HMAC SKU");
        product = productRepository.save(product);
        variant(tenantId, product.getId(), "HMAC-SKU");
        channelService.upsert(new IntegrationChannelUpsertRequest(
                "SHOPIFY", "ACTIVE",
                Map.of("webhookSecret", "hub-secret-" + slug),
                Map.of()));
        TenantContext.clear();

        String payload = "{\"name\":\"#HMAC-1\",\"line_items\":[{\"sku\":\"HMAC-SKU\",\"quantity\":1,\"price\":\"1\"}]}";
        mockMvc.perform(post("/api/v1/integrations/inbound/SHOPIFY")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header(ShopifyOrderAdapter.HEADER_HMAC, "not-valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        String goodHmac = hmacBase64("hub-secret-" + slug, payload);
        mockMvc.perform(post("/api/v1/integrations/inbound/SHOPIFY")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header(ShopifyOrderAdapter.HEADER_HMAC, goodHmac)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalOrderRef").value("#HMAC-1"));
    }

    @Test
    void ediInboundCreatesSalesOrderFromX12() throws Exception {
        String slug = "inb-e-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Inbound EDI", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();

        TenantContext.setTenantId(tenantId);
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("EDI Partner Customer");
        customer = customerRepository.save(customer);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("EDI");
        product.setName("EDI Widget");
        product = productRepository.save(product);

        ProductVariant widget = variant(tenantId, product.getId(), "WIDGET-EDI");

        EdiTradingPartner partner = new EdiTradingPartner();
        partner.setTenantId(tenantId);
        partner.setCustomerId(customer.getId());
        partner.setAs2Id("AS2-" + slug);
        partner = ediTradingPartnerRepository.save(partner);
        TenantContext.clear();

        String x12 = "ISA*00*          *00*          *ZZ*PARTNER        *ZZ*INVSYS         *"
                + "260713*1200*U*00401*000000001*0*P*>~"
                + "ST*850*0001~"
                + "BEG*00*NE*PO-INB-999**260713~"
                + "PO1*4*EA*10.00**VP*WIDGET-EDI~"
                + "SE*4*0001~"
                + "GE*1*0001~"
                + "IEA*1*000000001~";

        MvcResult result = mockMvc.perform(post("/api/v1/integrations/inbound/EDI")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .header("X-Trading-Partner-Id", partner.getId().toString())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(x12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("EDI"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.externalOrderRef").value("PO-INB-999"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID orderId = UUID.fromString(body.get("id").asString());

        TenantContext.setTenantId(tenantId);
        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow();
        assertThat(order.getCustomerId()).isEqualTo(customer.getId());
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(orderId);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().getVariantId()).isEqualTo(widget.getId());
        assertThat(lines.getFirst().getQtyOrdered()).isEqualByComparingTo("4");
    }

    @Test
    void unsupportedChannelRejected() throws Exception {
        String slug = "inb-u-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Inbound Bad", slug, "owner@" + slug + ".test", "password123", "Owner"));

        mockMvc.perform(post("/api/v1/integrations/inbound/AMAZON")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"#1\",\"line_items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private ProductVariant variant(UUID tenantId, UUID productId, String sku) {
        ProductVariant v = new ProductVariant();
        v.setTenantId(tenantId);
        v.setProductId(productId);
        v.setSku(sku);
        return variantRepository.save(v);
    }

    private static String hmacBase64(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
