package com.invsys;

import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.domain.SoftKitComponent;
import com.invsys.domain.WebhookEvent;
import com.invsys.integration.outbox.ChannelOrderWebhookHandler;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.repository.SoftKitComponentRepository;
import com.invsys.repository.WebhookEventRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SoftKitShopifyExplodeTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ChannelOrderWebhookHandler channelOrderWebhookHandler;
    @Autowired WebhookEventRepository webhookEventRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SoftKitComponentRepository softKitComponentRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shopifyOrderExplodesSoftKitIntoComponentLines() {
        UUID tenantId = testDataHelper.createTenant("SoftKit Co", "sk-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Shopify Customer");
        customerRepository.save(customer);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("SK");
        product.setName("Soft Kit Bundle");
        product = productRepository.save(product);

        ProductVariant kit = new ProductVariant();
        kit.setTenantId(tenantId);
        kit.setProductId(product.getId());
        kit.setSku("BUNDLE-1");
        kit.setSoftKit(true);
        final ProductVariant savedKit = variantRepository.save(kit);

        ProductVariant a = component(tenantId, product.getId(), "COMP-A");
        ProductVariant b = component(tenantId, product.getId(), "COMP-B");

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

        WebhookEvent event = new WebhookEvent();
        event.setTenantId(tenantId);
        event.setSource("SHOPIFY");
        event.setExternalEventId("ord-" + UUID.randomUUID());
        event.setSignatureValid(true);
        Map<String, Object> payload = new HashMap<>();
        payload.put("topic", "orders/create");
        payload.put("name", "#1001");
        payload.put("line_items", List.of(Map.of(
                "sku", "BUNDLE-1",
                "quantity", 3,
                "price", "29.99"
        )));
        event.setPayload(payload);
        event = webhookEventRepository.save(event);
        TenantContext.clear();

        channelOrderWebhookHandler.process(event);

        TenantContext.setTenantId(tenantId);
        WebhookEvent processed = webhookEventRepository.findById(event.getId()).orElseThrow();
        assertThat(processed.getError()).isNull();
        assertThat(processed.getProcessedAt()).isNotNull();
        var orders = salesOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        assertThat(orders).hasSize(1);
        List<SalesOrderLine> lines = salesOrderLineRepository.findBySalesOrderId(orders.getFirst().getId());
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
    }

    private ProductVariant component(UUID tenantId, UUID productId, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku(sku);
        return variantRepository.save(variant);
    }
}
