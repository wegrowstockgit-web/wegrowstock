package com.invsys;

import com.invsys.domain.Customer;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.Tenant;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.service.AllocationService;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationConcurrencyTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired TenantRepository tenantRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired InventoryService inventoryService;
    @Autowired AllocationService allocationService;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void concurrentAllocationDoesNotOversell() throws Exception {
        UUID tenantId = testDataHelper.createTenant("Conc Tenant", "conc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("CONC");
        product.setName("Concurrency Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("CONC-V1");
        variant = variantRepository.save(variant);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-C");
        location.setName("Conc WH");
        location.setPath("/WH-C");
        location = locationRepository.save(location);
        final UUID locationId = location.getId();

        inventoryService.receive(variant.getId(), locationId, null, new BigDecimal("15"), null, null);

        SalesOrder order1 = createOrder(tenantId, variant.getId(), new BigDecimal("10"), "SO-1", createCustomer(tenantId));
        SalesOrder order2 = createOrder(tenantId, variant.getId(), new BigDecimal("10"), "SO-2", createCustomer(tenantId));
        final SalesOrderLine line1 = salesOrderLineRepository.findAll().stream()
                .filter(line -> order1.getId().equals(line.getSalesOrderId()))
                .findFirst().orElseThrow();
        final SalesOrderLine line2 = salesOrderLineRepository.findAll().stream()
                .filter(line -> order2.getId().equals(line.getSalesOrderId()))
                .findFirst().orElseThrow();

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        Runnable task1 = () -> runAllocate(tenantId, line1.getId(), locationId, start, successes);
        Runnable task2 = () -> runAllocate(tenantId, line2.getId(), locationId, start, successes);
        pool.submit(task1);
        pool.submit(task2);
        start.countDown();
        pool.shutdown();
        while (!pool.isTerminated()) {
            Thread.sleep(50);
        }

        BigDecimal totalAllocated = salesOrderLineRepository.findAll().stream()
                .map(SalesOrderLine::getQtyAllocated)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalAllocated).isLessThanOrEqualTo(new BigDecimal("15"));
        assertThat(successes.get()).isGreaterThanOrEqualTo(1);
    }

    private void runAllocate(UUID tenantId, UUID lineId, UUID locationId, CountDownLatch start, AtomicInteger successes) {
        try {
            start.await();
            TenantContext.setTenantId(tenantId);
            SalesOrderLine line = salesOrderLineRepository.findById(lineId).orElseThrow();
            allocationService.allocate(line, List.of(locationId));
            successes.incrementAndGet();
        } catch (Exception ignored) {
        } finally {
            TenantContext.clear();
        }
    }

    private Customer createCustomer(UUID tenantId) {
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Test Customer");
        return customerRepository.save(customer);
    }

    private SalesOrder createOrder(UUID tenantId, UUID variantId, BigDecimal qty, String number, Customer customer) {
        transactionTemplate.executeWithoutResult(status -> {
            TenantContext.setTenantId(tenantId);
            SalesOrder order = new SalesOrder();
            order.setTenantId(tenantId);
            order.setCustomerId(customer.getId());
            order.setNumber(number);
            order.setStatus("CONFIRMED");
            salesOrderRepository.saveAndFlush(order);

            SalesOrderLine line = new SalesOrderLine();
            line.setTenantId(tenantId);
            line.setSalesOrderId(order.getId());
            line.setVariantId(variantId);
            line.setQtyOrdered(qty);
            salesOrderLineRepository.saveAndFlush(line);
        });
        TenantContext.setTenantId(tenantId);
        return salesOrderRepository.findAll().stream()
                .filter(order -> number.equals(order.getNumber()))
                .findFirst()
                .orElseThrow();
    }
}
