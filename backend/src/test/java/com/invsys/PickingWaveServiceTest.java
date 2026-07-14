package com.invsys;

import com.invsys.domain.Allocation;
import com.invsys.domain.Customer;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.repository.AllocationRepository;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.service.PickingWaveService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PickingWaveServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired PickingWaveService pickingWaveService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void generateWaveOrdersTasksByLocationPath() {
        UUID tenantId = testDataHelper.createTenant("Wave Tenant", "wave-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("WAVE");
        product.setName("Wave Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("WAVE-V1");
        variant = variantRepository.save(variant);

        Location locA = new Location();
        locA.setTenantId(tenantId);
        locA.setType("BIN");
        locA.setCode("A-1");
        locA.setName("Bin A");
        locA.setPath("WH-01/A-1");
        locA = locationRepository.save(locA);

        Location locB = new Location();
        locB.setTenantId(tenantId);
        locB.setType("BIN");
        locB.setCode("B-1");
        locB.setName("Bin B");
        locB.setPath("WH-01/B-1");
        locB = locationRepository.save(locB);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Wave Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-WAVE-1");
        order.setStatus("ALLOCATED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(BigDecimal.ONE);
        line = salesOrderLineRepository.save(line);

        Allocation allocB = new Allocation();
        allocB.setTenantId(tenantId);
        allocB.setSalesOrderLineId(line.getId());
        allocB.setVariantId(variant.getId());
        allocB.setLocationId(locB.getId());
        allocB.setQuantity(BigDecimal.ONE);
        allocB.setStatus("ACTIVE");
        allocationRepository.save(allocB);

        Allocation allocA = new Allocation();
        allocA.setTenantId(tenantId);
        allocA.setSalesOrderLineId(line.getId());
        allocA.setVariantId(variant.getId());
        allocA.setLocationId(locA.getId());
        allocA.setQuantity(BigDecimal.ONE);
        allocA.setStatus("ACTIVE");
        allocationRepository.save(allocA);

        PickingWaveService.WaveResult result = pickingWaveService.generateWave(null, null);
        result = pickingWaveService.releaseWave(result.wave().getId());
        List<String> paths = result.tasks().stream().map(t -> t.getLocationPath()).toList();

        assertThat(result.wave().getStatus()).isEqualTo("RELEASED");
        assertThat(paths).hasSize(2);
        assertThat(paths).containsExactly("WH-01/A-1", "WH-01/B-1");
    }
}
