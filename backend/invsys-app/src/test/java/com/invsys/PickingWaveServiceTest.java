package com.invsys;

import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.inventory.repository.AllocationRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.service.PickingWaveService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.invsys.domain.Tenant;

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

        List<PickingWaveService.WavePick> picks = pickingWaveService.listPicksByPath(result.wave().getId());
        assertThat(picks).hasSize(2);
        assertThat(picks.stream().map(PickingWaveService.WavePick::locationPath).toList())
                .containsExactly("WH-01/A-1", "WH-01/B-1");
    }

    @Test
    void optimizeWaveEmitsPathOrderedManifest() {
        UUID tenantId = testDataHelper.createTenant("Opt Tenant", "opt-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("OPT");
        product.setName("Opt Product");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("OPT-V1");
        variant = variantRepository.save(variant);

        Location locFar = new Location();
        locFar.setTenantId(tenantId);
        locFar.setType("BIN");
        locFar.setCode("Z-9");
        locFar.setName("Bin Z");
        locFar.setPath("WH-01/Z-PICK/A-9/B-99");
        locFar = locationRepository.save(locFar);

        Location locNear = new Location();
        locNear.setTenantId(tenantId);
        locNear.setType("BIN");
        locNear.setCode("A-1");
        locNear.setName("Bin A");
        locNear.setPath("WH-01/Z-PICK/A-1/B-01");
        locNear = locationRepository.save(locNear);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Opt Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-OPT-1");
        order.setStatus("ALLOCATED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line = salesOrderLineRepository.save(line);

        Allocation far = new Allocation();
        far.setTenantId(tenantId);
        far.setSalesOrderLineId(line.getId());
        far.setVariantId(variant.getId());
        far.setLocationId(locFar.getId());
        far.setQuantity(BigDecimal.ONE);
        far.setStatus("ACTIVE");
        allocationRepository.save(far);

        Allocation near = new Allocation();
        near.setTenantId(tenantId);
        near.setSalesOrderLineId(line.getId());
        near.setVariantId(variant.getId());
        near.setLocationId(locNear.getId());
        near.setQuantity(BigDecimal.ONE);
        near.setStatus("ACTIVE");
        allocationRepository.save(near);

        PickingWaveService.OptimizeResult optimized =
                pickingWaveService.optimizeWave(List.of(order.getId()));

        assertThat(optimized.waveId()).isNotNull();
        assertThat(optimized.status()).isEqualTo("DRAFT");
        assertThat(optimized.manifest()).hasSize(2);
        assertThat(optimized.manifest().get(0).locationPath()).isEqualTo("WH-01/Z-PICK/A-1/B-01");
        assertThat(optimized.manifest().get(1).locationPath()).isEqualTo("WH-01/Z-PICK/A-9/B-99");
        assertThat(optimized.manifest().get(0).pathSegments()).containsExactly("WH-01", "Z-PICK", "A-1", "B-01");
        assertThat(optimized.manifest().get(0).sequenceOrder()).isEqualTo(1);
        assertThat(optimized.manifest().get(1).sequenceOrder()).isEqualTo(2);
    }
}
