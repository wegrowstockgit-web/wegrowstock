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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationPolicyTest extends AbstractIntegrationTest {

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
    void shipCompleteLeavesOrderUnallocatedWhenAnyLineIsShort() {
        UUID tenantId = seedTenant();
        ProductVariant full = saveVariant(tenantId, "ATP-FULL");
        ProductVariant shortSku = saveVariant(tenantId, "ATP-SHORT");
        Location warehouse = saveWarehouse(tenantId);
        inventoryService.receive(full.getId(), warehouse.getId(), null, new BigDecimal("10"), null, null);
        inventoryService.receive(shortSku.getId(), warehouse.getId(), null, new BigDecimal("1"), null, null);

        SalesOrder order = confirmedOrder(tenantId, AllocationPolicy.SHIP_COMPLETE);
        addLine(tenantId, order.getId(), full.getId(), new BigDecimal("5"));
        addLine(tenantId, order.getId(), shortSku.getId(), new BigDecimal("4"));

        SalesOrder result = salesOrderService.allocate(order.getId());
        assertThat(result.getStatus()).isEqualTo(SalesOrderStatus.BACKORDERED.name());
        for (SalesOrderLine line : lineRepository.findBySalesOrderId(order.getId())) {
            assertThat(line.getQtyAllocated()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(line.getQtyBackordered()).isEqualByComparingTo(line.getQtyOrdered());
        }
    }

    @Test
    void allowPartialAllocatesAvailableAndBackordersRemainder() {
        UUID tenantId = seedTenant();
        ProductVariant variant = saveVariant(tenantId, "ATP-PART");
        Location warehouse = saveWarehouse(tenantId);
        inventoryService.receive(variant.getId(), warehouse.getId(), null, new BigDecimal("3"), null, null);

        SalesOrder order = confirmedOrder(tenantId, AllocationPolicy.ALLOW_PARTIAL);
        addLine(tenantId, order.getId(), variant.getId(), new BigDecimal("8"));

        SalesOrder result = salesOrderService.allocate(order.getId());
        assertThat(result.getStatus()).isEqualTo(SalesOrderStatus.PARTIALLY_ALLOCATED.name());
        SalesOrderLine line = lineRepository.findBySalesOrderId(order.getId()).getFirst();
        assertThat(line.getQtyAllocated()).isEqualByComparingTo("3");
        assertThat(line.getQtyBackordered()).isEqualByComparingTo("5");
    }

    @Test
    void shipCompleteAllocatesWhenEveryLineHasAtp() {
        UUID tenantId = seedTenant();
        ProductVariant variant = saveVariant(tenantId, "ATP-OK");
        Location warehouse = saveWarehouse(tenantId);
        inventoryService.receive(variant.getId(), warehouse.getId(), null, new BigDecimal("6"), null, null);

        SalesOrder order = confirmedOrder(tenantId, AllocationPolicy.SHIP_COMPLETE);
        addLine(tenantId, order.getId(), variant.getId(), new BigDecimal("6"));

        SalesOrder result = salesOrderService.allocate(order.getId());
        assertThat(result.getStatus()).isEqualTo(SalesOrderStatus.ALLOCATED.name());
        SalesOrderLine line = lineRepository.findBySalesOrderId(order.getId()).getFirst();
        assertThat(line.getQtyAllocated()).isEqualByComparingTo("6");
        assertThat(line.getQtyBackordered()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private UUID seedTenant() {
        UUID tenantId = testDataHelper.createTenant("ATP Co", "atp-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        return tenantId;
    }

    private SalesOrder confirmedOrder(UUID tenantId, AllocationPolicy policy) {
        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("ATP Buyer");
        customer = customerRepository.save(customer);
        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-ATP-" + UUID.randomUUID().toString().substring(0, 6));
        order.setStatus(SalesOrderStatus.CONFIRMED.name());
        order.setAllocationPolicy(policy);
        return salesOrderRepository.save(order);
    }

    private void addLine(UUID tenantId, UUID orderId, UUID variantId, BigDecimal qty) {
        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(orderId);
        line.setVariantId(variantId);
        line.setQtyOrdered(qty);
        line.setUnitPrice(BigDecimal.TEN);
        lineRepository.save(line);
    }

    private ProductVariant saveVariant(UUID tenantId, String sku) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(sku);
        product.setName(sku);
        product = productRepository.save(product);
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku(sku);
        return variantRepository.save(variant);
    }

    private Location saveWarehouse(UUID tenantId) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-ATP");
        location.setName("ATP WH");
        location.setPath("/WH-ATP");
        return locationRepository.save(location);
    }
}
