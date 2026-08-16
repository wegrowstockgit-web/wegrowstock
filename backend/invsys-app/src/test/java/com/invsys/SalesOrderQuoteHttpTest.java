package com.invsys;

import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.api.SalesOrderController;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.domain.SalesOrderStatus;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderQuoteHttpTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired SalesOrderController salesOrderController;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository lineRepository;

    @BeforeEach
    void auth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "owner@test",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void repCanPricePendingQuoteAndListShowsPolicy() {
        UUID tenantId = testDataHelper.createTenant("Quote HTTP", "qhttp-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("QH");
        product.setName("Quote HTTP Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("QH-1");
        variant = variantRepository.save(variant);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Quote HTTP Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-QHTTP-1");
        order.setStatus(SalesOrderStatus.PENDING_REP_APPROVAL.name());
        order.setQuoteNotes("Need better price");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("5"));
        line.setUnitPrice(new BigDecimal("20.00"));
        line = lineRepository.save(line);

        SalesOrder priced = salesOrderController.updateQuote(order.getId(), new SalesOrderController.UpdateQuoteRequest(
                List.of(new SalesOrderController.QuoteLinePriceRequest(line.getId(), new BigDecimal("16.00"))),
                new BigDecimal("8.00"),
                Instant.now().plus(14, ChronoUnit.DAYS),
                "NET 45 courtesy"));
        assertThat(priced.getStatus()).isEqualTo(SalesOrderStatus.QUOTE_READY.name());
        assertThat(priced.getManualDiscountTotal()).isEqualByComparingTo("8.00");

        List<SalesOrderController.SalesOrderResponse> listed = salesOrderController.listSalesOrders();
        assertThat(listed).anySatisfy(row -> {
            assertThat(row.number()).isEqualTo("SO-QHTTP-1");
            assertThat(row.status()).isEqualTo(SalesOrderStatus.QUOTE_READY.name());
            assertThat(row.allocationPolicy()).isEqualTo("ALLOW_PARTIAL");
        });

        SalesOrderController.SalesOrderDetailResponse detail = salesOrderController.getSalesOrder(order.getId());
        assertThat(detail.quoteNotes()).isEqualTo("NET 45 courtesy");
        assertThat(detail.lines().getFirst().unitPrice()).isEqualByComparingTo("16.00");
        assertThat(detail.lines().getFirst().qtyAllocated()).isEqualByComparingTo("0");
    }
}
