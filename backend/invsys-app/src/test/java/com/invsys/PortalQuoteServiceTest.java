package com.invsys;

import com.invsys.api.dto.PortalOrderResponse;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.domain.AllocationPolicy;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.domain.SalesOrderStatus;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.service.SalesOrderService;
import com.invsys.service.PortalService;
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

class PortalQuoteServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired PortalService portalService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SalesOrderLineRepository lineRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void requestQuoteCreatesPendingRepApprovalWithoutInstantCheckoutStatus() {
        UUID tenantId = testDataHelper.createTenant("Portal RFQ", "prfq-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Portal Buyer");
        customer = customerRepository.save(customer);
        TenantContext.setCustomerId(customer.getId());

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PQ");
        product.setName("Portal Widget");
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PQ-1");
        variant.setPrice(new BigDecimal("50.0000"));
        variant = variantRepository.save(variant);

        PortalOrderResponse quote = portalService.requestQuote(
                List.of(new PortalService.PortalOrderLineInput(variant.getId(), new BigDecimal("4"))),
                "PO-RFQ-1",
                null,
                AllocationPolicy.SHIP_COMPLETE,
                "Need pallet pricing");
        assertThat(quote.status()).isEqualTo(SalesOrderStatus.PENDING_REP_APPROVAL.name());
        assertThat(quote.allocationPolicy()).isEqualTo("SHIP_COMPLETE");
        assertThat(quote.quoteNotes()).isEqualTo("Need pallet pricing");

        SalesOrderLine line = lineRepository.findBySalesOrderId(quote.id()).getFirst();
        salesOrderService.updateQuotePricing(
                quote.id(),
                List.of(new SalesOrderService.QuoteLinePrice(line.getId(), new BigDecimal("40.00"))),
                new BigDecimal("10.00"),
                Instant.now().plus(14, ChronoUnit.DAYS),
                "Approved 20%");

        PortalOrderResponse accepted = portalService.acceptQuote(quote.id());
        assertThat(accepted.status()).isIn(
                SalesOrderStatus.UNALLOCATED.name(),
                SalesOrderStatus.BACKORDERED.name(),
                SalesOrderStatus.ALLOCATED.name(),
                SalesOrderStatus.PARTIALLY_ALLOCATED.name());
        assertThat(accepted.manualDiscountTotal()).isEqualByComparingTo("10.00");
    }
}
