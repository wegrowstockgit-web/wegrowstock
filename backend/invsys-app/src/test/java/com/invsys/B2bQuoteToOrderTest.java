package com.invsys;

import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.modules.sales.domain.AllocationPolicy;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.domain.SalesOrderStatus;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.sales.service.SalesOrderService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class B2bQuoteToOrderTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired SalesOrderService salesOrderService;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository lineRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void quoteLifecyclePricesExpireAndConvertToAllocatedOrder() {
        UUID tenantId = seedTenant();
        ProductVariant variant = saveVariant(tenantId, "RFQ-1", new BigDecimal("40.00"));
        Location warehouse = saveWarehouse(tenantId);
        inventoryService.receive(variant.getId(), warehouse.getId(), null, new BigDecimal("10"), null, null);

        SalesOrder quote = draftOrder(tenantId, variant.getId(), new BigDecimal("2"), new BigDecimal("40.00"));
        SalesOrder pending = salesOrderService.requestQuote(quote.getId(), "Need NET 45");
        assertThat(pending.getStatus()).isEqualTo(SalesOrderStatus.PENDING_REP_APPROVAL.name());
        assertThat(pending.getQuoteNotes()).isEqualTo("Need NET 45");

        SalesOrderLine line = lineRepository.findBySalesOrderId(quote.getId()).getFirst();
        Instant expiry = Instant.now().plus(14, ChronoUnit.DAYS);
        SalesOrder ready = salesOrderService.updateQuotePricing(
                quote.getId(),
                List.of(new SalesOrderService.QuoteLinePrice(line.getId(), new BigDecimal("32.00"))),
                new BigDecimal("5.00"),
                expiry,
                "Volume courtesy");
        assertThat(ready.getStatus()).isEqualTo(SalesOrderStatus.QUOTE_READY.name());
        assertThat(ready.getManualDiscountTotal()).isEqualByComparingTo("5.00");
        assertThat(lineRepository.findById(line.getId()).orElseThrow().getUnitPrice())
                .isEqualByComparingTo("32.00");

        SalesOrder accepted = salesOrderService.acceptQuote(quote.getId());
        assertThat(accepted.getStatus()).isEqualTo(SalesOrderStatus.ALLOCATED.name());
        assertThat(lineRepository.findById(line.getId()).orElseThrow().getQtyAllocated())
                .isEqualByComparingTo("2");
    }

    @Test
    void acceptQuoteRejectsExpiredQuote() {
        UUID tenantId = seedTenant();
        ProductVariant variant = saveVariant(tenantId, "RFQ-EXP", new BigDecimal("10.00"));
        SalesOrder quote = draftOrder(tenantId, variant.getId(), BigDecimal.ONE, new BigDecimal("10.00"));
        salesOrderService.requestQuote(quote.getId(), "rush");
        SalesOrderLine line = lineRepository.findBySalesOrderId(quote.getId()).getFirst();
        salesOrderService.updateQuotePricing(
                quote.getId(),
                List.of(new SalesOrderService.QuoteLinePrice(line.getId(), new BigDecimal("9.00"))),
                BigDecimal.ZERO,
                Instant.now().plus(2, ChronoUnit.DAYS),
                null);
        UUID quoteId = quote.getId();
        SalesOrder expired = salesOrderRepository.findById(quoteId).orElseThrow();
        expired.setQuoteExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        salesOrderRepository.save(expired);

        assertThatThrownBy(() -> salesOrderService.acceptQuote(quoteId))
                .hasMessageContaining("expired");
    }

    @Test
    void portalQuoteRequestLeavesDraftCheckoutUntouched() {
        UUID tenantId = seedTenant();
        ProductVariant variant = saveVariant(tenantId, "RFQ-DRAFT", new BigDecimal("12.00"));
        SalesOrder draft = draftOrder(tenantId, variant.getId(), BigDecimal.ONE, new BigDecimal("12.00"));
        assertThat(draft.getStatus()).isEqualTo(SalesOrderStatus.DRAFT.name());
        assertThat(draft.getAllocationPolicy()).isEqualTo(AllocationPolicy.ALLOW_PARTIAL);
    }

    private UUID seedTenant() {
        UUID tenantId = testDataHelper.createTenant("RFQ Co", "rfq-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        return tenantId;
    }

    private SalesOrder draftOrder(UUID tenantId, UUID variantId, BigDecimal qty, BigDecimal price) {
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Wholesale Buyer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-RFQ-" + UUID.randomUUID().toString().substring(0, 6));
        order.setStatus(SalesOrderStatus.DRAFT.name());
        order.setChannel("PORTAL");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variantId);
        line.setQtyOrdered(qty);
        line.setUnitPrice(price);
        lineRepository.save(line);
        return order;
    }

    private ProductVariant saveVariant(UUID tenantId, String sku, BigDecimal price) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(sku);
        product.setName(sku);
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku(sku);
        variant.setPrice(price);
        return variantRepository.save(variant);
    }

    private Location saveWarehouse(UUID tenantId) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-RFQ");
        location.setName("RFQ WH");
        location.setPath("/WH-RFQ");
        return locationRepository.save(location);
    }
}
