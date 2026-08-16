package com.invsys;

import com.invsys.modules.sales.api.SalesOrderController;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderDetailContractTest extends AbstractIntegrationTest {

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
    void salesOrderDetailIncludesSkuAndNameForPeekDrawer() {
        UUID tenantId = testDataHelper.createTenant("Peek Co", "peek-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("PEEK");
        product.setName("Peek Widget");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("PEEK-01");
        variant = variantRepository.save(variant);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Peek Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-PEEK-1");
        order.setStatus("DRAFT");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("3"));
        line.setUnitPrice(new BigDecimal("12.00"));
        lineRepository.save(line);

        SalesOrderController.SalesOrderDetailResponse detail = salesOrderController.getSalesOrder(order.getId());

        assertThat(detail.lines()).hasSize(1);
        assertThat(detail.lines().getFirst().sku()).isEqualTo("PEEK-01");
        assertThat(detail.lines().getFirst().name()).isEqualTo("Peek Widget");
        assertThat(detail.lines().getFirst().qtyOrdered()).isEqualByComparingTo("3");
        assertThat(detail.allocationPolicy()).isEqualTo("ALLOW_PARTIAL");
        assertThat(detail.lines().getFirst().qtyBackordered()).isEqualByComparingTo("0");
    }
}
