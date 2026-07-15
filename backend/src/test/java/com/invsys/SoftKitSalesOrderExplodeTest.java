package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.Customer;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.SoftKitComponent;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.SoftKitComponentRepository;
import com.invsys.service.SoftKitExplosionService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SoftKitSalesOrderExplodeTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SoftKitComponentRepository softKitComponentRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired SoftKitExplosionService softKitExplosionService;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createSalesOrderExplodesSoftKitIntoComponentLines() throws Exception {
        String slug = "sosk-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "SO SoftKit", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Direct Customer");
        customer = customerRepository.save(customer);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("SOSK");
        product.setName("Soft Kit Bundle");
        product = productRepository.save(product);

        ProductVariant kit = new ProductVariant();
        kit.setTenantId(tenantId);
        kit.setProductId(product.getId());
        kit.setSku("KIT-SO-1");
        kit.setSoftKit(true);
        kit = variantRepository.save(kit);

        ProductVariant a = component(tenantId, product.getId(), "SO-COMP-A");
        ProductVariant b = component(tenantId, product.getId(), "SO-COMP-B");

        SoftKitComponent c1 = new SoftKitComponent();
        c1.setTenantId(tenantId);
        c1.setParentKitId(kit.getId());
        c1.setComponentId(a.getId());
        c1.setQuantity(new BigDecimal("2"));
        softKitComponentRepository.save(c1);

        SoftKitComponent c2 = new SoftKitComponent();
        c2.setTenantId(tenantId);
        c2.setParentKitId(kit.getId());
        c2.setComponentId(b.getId());
        c2.setQuantity(new BigDecimal("1"));
        softKitComponentRepository.save(c2);

        String body = """
                {
                  "customerId": "%s",
                  "number": "SO-SK-%s",
                  "lines": [
                    { "variantId": "%s", "qtyOrdered": 3, "unitPrice": 29.99 }
                  ]
                }
                """.formatted(customer.getId(), UUID.randomUUID().toString().substring(0, 6), kit.getId());

        String response = mockMvc.perform(post("/api/v1/sales-orders")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Request filter clears TenantContext; restore for post-request repository reads under RLS.
        TenantContext.setTenantId(tenantId);
        UUID orderId = UUID.fromString(response.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(orderId);

        assertThat(lines).hasSize(2);
        assertThat(lines.stream().map(SalesOrderLine::getVariantId).toList())
                .containsExactlyInAnyOrder(a.getId(), b.getId())
                .doesNotContain(kit.getId());

        SalesOrderLine lineA = lines.stream().filter(l -> l.getVariantId().equals(a.getId())).findFirst().orElseThrow();
        SalesOrderLine lineB = lines.stream().filter(l -> l.getVariantId().equals(b.getId())).findFirst().orElseThrow();
        assertThat(lineA.getQtyOrdered()).isEqualByComparingTo("6");
        assertThat(lineB.getQtyOrdered()).isEqualByComparingTo("3");
        assertThat(lineA.getUnitPrice().add(lineB.getUnitPrice())).isEqualByComparingTo("29.99");

        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void explosionServicePassthroughForNonKit() {
        String slug = "pt-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Passthrough", slug, "owner@" + slug + ".test", "password123", "Owner"));
        UUID tenantId = owner.tenantId();
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PT");
        product.setName("Plain");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PT-1");
        variant = variantRepository.save(variant);

        var lines = softKitExplosionService.explode(
                tenantId, variant.getId(), new BigDecimal("5"), new BigDecimal("10"), true, true);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().variantId()).isEqualTo(variant.getId());
        assertThat(lines.getFirst().quantity()).isEqualByComparingTo("5");
        assertThat(lines.getFirst().unitPrice()).isEqualByComparingTo("10");
    }

    private ProductVariant component(UUID tenantId, UUID productId, String sku) {
        ProductVariant v = new ProductVariant();
        v.setTenantId(tenantId);
        v.setProductId(productId);
        v.setSku(sku);
        return variantRepository.save(v);
    }
}
