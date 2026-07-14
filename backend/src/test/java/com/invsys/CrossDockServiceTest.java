package com.invsys;

import com.invsys.domain.Customer;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.service.CrossDockService;
import com.invsys.service.PickingWaveService;
import com.invsys.tenancy.TenantContext;
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
}
