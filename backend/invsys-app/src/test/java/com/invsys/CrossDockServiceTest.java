package com.invsys;

import com.invsys.modules.fulfillment.domain.Allocation;
import com.invsys.modules.sales.domain.Customer;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.purchasing.domain.PurchaseOrder;
import com.invsys.modules.purchasing.domain.PurchaseOrderLine;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.modules.purchasing.domain.Supplier;
import com.invsys.modules.fulfillment.repository.AllocationRepository;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.purchasing.repository.SupplierRepository;
import com.invsys.service.CrossDockService;
import com.invsys.service.PickingWaveService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CrossDockServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired CrossDockService crossDockService;
    @Autowired PickingWaveService pickingWaveService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void suggestsMatchBetweenOpenPoAndUnfulfilledSalesOrder() {
        UUID tenantId = testDataHelper.createTenant("XDock Co", "xdock-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Inbound");
        supplier = supplierRepository.save(supplier);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Outbound");
        customer = customerRepository.save(customer);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("XD");
        product.setName("Cross Dock Item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("XD-1");
        variant = variantRepository.save(variant);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-XD-1");
        po.setStatus("IN_TRANSIT");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine poLine = new PurchaseOrderLine();
        poLine.setTenantId(tenantId);
        poLine.setPurchaseOrderId(po.getId());
        poLine.setVariantId(variant.getId());
        poLine.setQtyOrdered(new BigDecimal("20"));
        poLine.setQtyReceived(BigDecimal.ZERO);
        poLine.setUnitCost(new BigDecimal("5"));
        purchaseOrderLineRepository.save(poLine);

        SalesOrder so = new SalesOrder();
        so.setTenantId(tenantId);
        so.setCustomerId(customer.getId());
        so.setNumber("SO-XD-1");
        so.setStatus("CONFIRMED");
        so = salesOrderRepository.save(so);

        SalesOrderLine soLine = new SalesOrderLine();
        soLine.setTenantId(tenantId);
        soLine.setSalesOrderId(so.getId());
        soLine.setVariantId(variant.getId());
        soLine.setQtyOrdered(new BigDecimal("8"));
        soLine.setUnitPrice(new BigDecimal("12"));
        salesOrderLineRepository.save(soLine);

        List<CrossDockService.CrossDockSuggestion> suggestions = crossDockService.suggestions();
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.getFirst().variantId()).isEqualTo(variant.getId());
        assertThat(suggestions.getFirst().suggestedQty()).isEqualByComparingTo("8");
        assertThat(suggestions.getFirst().inboundOpenQty()).isEqualByComparingTo("20");
        assertThat(suggestions.getFirst().purchaseOrderId()).isEqualTo(po.getId());
        assertThat(suggestions.getFirst().salesOrderId()).isEqualTo(so.getId());

        // Wave service exposes the same checker for GET /api/v1/picking/cross-dock/suggestions
        List<CrossDockService.CrossDockSuggestion> viaWave = pickingWaveService.crossDockSuggestions();
        assertThat(viaWave).hasSize(suggestions.size());
        assertThat(viaWave.getFirst().variantId()).isEqualTo(variant.getId());
    }

    @Test
    void checkVariantRoutesActiveAllocationToShippingStaging() {
        UUID tenantId = testDataHelper.createTenant("XDock Check", "xdock-chk-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Outbound");
        customer = customerRepository.save(customer);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("XDC");
        product.setName("Cross Dock Check");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("XDC-1");
        variant = variantRepository.save(variant);

        Location bin = new Location();
        bin.setTenantId(tenantId);
        bin.setType("BIN");
        bin.setCode("STAGE-1");
        bin.setName("Staging");
        bin.setPath("/WH/STAGE-1");
        bin = locationRepository.save(bin);

        SalesOrder so = new SalesOrder();
        so.setTenantId(tenantId);
        so.setCustomerId(customer.getId());
        so.setNumber("SO-XDC-1");
        so.setStatus("ALLOCATED");
        so = salesOrderRepository.save(so);

        SalesOrderLine soLine = new SalesOrderLine();
        soLine.setTenantId(tenantId);
        soLine.setSalesOrderId(so.getId());
        soLine.setVariantId(variant.getId());
        soLine.setQtyOrdered(new BigDecimal("3"));
        soLine.setUnitPrice(new BigDecimal("10"));
        soLine = salesOrderLineRepository.save(soLine);

        Allocation allocation = new Allocation();
        allocation.setTenantId(tenantId);
        allocation.setSalesOrderLineId(soLine.getId());
        allocation.setVariantId(variant.getId());
        allocation.setLocationId(bin.getId());
        allocation.setQuantity(new BigDecimal("3"));
        allocation.setStatus("ACTIVE");
        allocation = allocationRepository.save(allocation);

        CrossDockService.CrossDockTask none = crossDockService.checkVariant(UUID.randomUUID());
        assertThat(none.match()).isFalse();

        CrossDockService.CrossDockTask task = crossDockService.checkVariant(variant.getId());
        assertThat(task.match()).isTrue();
        assertThat(task.allocationId()).isEqualTo(allocation.getId());
        assertThat(task.allocationStatus()).isEqualTo(CrossDockService.STATUS_CROSS_DOCK_ROUTED);
        assertThat(task.instruction()).contains("Shipping Staging Lane");
        assertThat(task.instruction()).contains("SO-XDC-1");

        Allocation refreshed = allocationRepository.findByTenantIdAndId(tenantId, allocation.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(CrossDockService.STATUS_CROSS_DOCK_ROUTED);
    }
}
