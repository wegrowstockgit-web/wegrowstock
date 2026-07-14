package com.invsys;

import com.invsys.domain.Customer;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InventoryLevelRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuarantineLocationAtpTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLevelRepository inventoryLevelRepository;
    @Autowired InventoryService inventoryService;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void availableForAllocationExcludesQuarantineStock() {
        UUID tenantId = testDataHelper.createTenant("Quarantine Co", "qat-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("QAT");
        product.setName("Quarantine Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("QAT-1");
        variant = variantRepository.save(variant);

        Location quarantine = new Location();
        quarantine.setTenantId(tenantId);
        quarantine.setType("QUARANTINE");
        quarantine.setCode("QUAR-1");
        quarantine.setName("Quarantine Hold");
        quarantine.setPath("/QUAR-1");
        quarantine = locationRepository.save(quarantine);

        Location pickable = new Location();
        pickable.setTenantId(tenantId);
        pickable.setType("BIN");
        pickable.setCode("BIN-PICK");
        pickable.setName("Pick Bin");
        pickable.setPath("/BIN-PICK");
        pickable = locationRepository.save(pickable);

        inventoryService.receive(variant.getId(), quarantine.getId(), null, new BigDecimal("50"), null, null);
        inventoryService.receive(variant.getId(), pickable.getId(), null, new BigDecimal("10"), null, null);

        var available = inventoryLevelRepository.findAvailableForAllocation(
                tenantId, variant.getId(), List.of(quarantine.getId(), pickable.getId()));

        assertThat(available).hasSize(1);
        assertThat(available.getFirst().getLocationId()).isEqualTo(pickable.getId());
        assertThat(available.getFirst().getAvailable()).isEqualByComparingTo("10");
    }

    @Test
    void quarantineReceiveHoldsAtpUntilRelease() {
        UUID tenantId = testDataHelper.createTenant("QA Hold", "qah-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("QAH");
        product.setName("QA Hold Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("QAH-1");
        variant = variantRepository.save(variant);

        Location quarantine = new Location();
        quarantine.setTenantId(tenantId);
        quarantine.setType("QUARANTINE");
        quarantine.setCode("QUAR-H");
        quarantine.setName("Quarantine");
        quarantine.setPath("/QUAR-H");
        quarantine = locationRepository.save(quarantine);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("QA Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-QAH-1");
        order.setStatus("SHIPPED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("8"));
        line.setQtyShipped(new BigDecimal("8"));
        line.setUnitPrice(BigDecimal.ONE);
        line = salesOrderLineRepository.save(line);

        inventoryService.quarantineReceive(
                variant.getId(), quarantine.getId(), null, new BigDecimal("8"),
                "RETURN", UUID.randomUUID(), line.getId());

        var held = inventoryLevelRepository.findAvailableForAllocation(
                tenantId, variant.getId(), List.of(quarantine.getId()));
        assertThat(held).isEmpty();

        inventoryService.releaseQuarantineHold(
                line.getId(), variant.getId(), quarantine.getId(), null,
                new BigDecimal("8"), "RESTOCK");

        // Location type QUARANTINE remains excluded from ATP even after hold release
        var afterRelease = inventoryLevelRepository.findAvailableForAllocation(
                tenantId, variant.getId(), List.of(quarantine.getId()));
        assertThat(afterRelease).isEmpty();
    }
}
