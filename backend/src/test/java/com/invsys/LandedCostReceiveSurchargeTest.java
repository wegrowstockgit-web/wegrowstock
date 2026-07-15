package com.invsys;

import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.service.PurchaseOrderService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LandedCostReceiveSurchargeTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired InventoryLedgerRepository ledgerRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void receiveDistributesLandedCostSurchargeIntoUnitCost() {
        UUID tenantId = testDataHelper.createTenant("Surcharge Co", "sur-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Inbound Vendor");
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("SUR");
        product.setName("Surcharge SKU");
        product = productRepository.save(product);

        ProductVariant a = new ProductVariant();
        a.setTenantId(tenantId);
        a.setProductId(product.getId());
        a.setSku("SUR-A");
        a = variantRepository.save(a);

        ProductVariant b = new ProductVariant();
        b.setTenantId(tenantId);
        b.setProductId(product.getId());
        b.setSku("SUR-B");
        b = variantRepository.save(b);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-SUR");
        wh.setName("Surcharge WH");
        wh.setPath("WH-SUR");
        wh = locationRepository.save(wh);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-SUR-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine lineA = new PurchaseOrderLine();
        lineA.setTenantId(tenantId);
        lineA.setPurchaseOrderId(po.getId());
        lineA.setVariantId(a.getId());
        lineA.setQtyOrdered(new BigDecimal("10"));
        lineA.setQtyReceived(BigDecimal.ZERO);
        lineA.setUnitCost(new BigDecimal("10.00"));
        lineA = purchaseOrderLineRepository.save(lineA);

        PurchaseOrderLine lineB = new PurchaseOrderLine();
        lineB.setTenantId(tenantId);
        lineB.setPurchaseOrderId(po.getId());
        lineB.setVariantId(b.getId());
        lineB.setQtyOrdered(new BigDecimal("10"));
        lineB.setQtyReceived(BigDecimal.ZERO);
        lineB.setUnitCost(new BigDecimal("30.00"));
        lineB = purchaseOrderLineRepository.save(lineB);

        // Values: A=100, B=300 → surcharge $40 → A share $10, B share $30
        // unit costs: A=10+1=11, B=30+3=33
        purchaseOrderService.receiveWithLandedCost(
                po.getId(),
                wh.getId(),
                new BigDecimal("40.00"),
                List.of(
                        new PurchaseOrderService.ReceiveLineInput(lineA.getId(), new BigDecimal("10"), null),
                        new PurchaseOrderService.ReceiveLineInput(lineB.getId(), new BigDecimal("10"), null)));

        InventoryLedger recvA = ledgerRepository
                .findByTenantIdAndReferenceTypeAndReferenceId(tenantId, "PURCHASE_ORDER_LINE", lineA.getId())
                .stream()
                .findFirst()
                .orElseThrow();
        InventoryLedger recvB = ledgerRepository
                .findByTenantIdAndReferenceTypeAndReferenceId(tenantId, "PURCHASE_ORDER_LINE", lineB.getId())
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(recvA.getUnitCost()).isEqualByComparingTo("11.000000");
        assertThat(recvB.getUnitCost()).isEqualByComparingTo("33.000000");
        assertThat(purchaseOrderRepository.findById(po.getId()).orElseThrow().getStatus()).isEqualTo("RECEIVED");
    }

    @Test
    void singleLineReceiveAppliesFullSurcharge() {
        UUID tenantId = testDataHelper.createTenant("Single Sur", "ss-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Vendor");
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("SS");
        product.setName("Single");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("SS-1");
        variant = variantRepository.save(variant);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-SS");
        wh.setName("WH");
        wh.setPath("WH-SS");
        wh = locationRepository.save(wh);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-SS-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("4"));
        line.setQtyReceived(BigDecimal.ZERO);
        line.setUnitCost(new BigDecimal("5.00"));
        line = purchaseOrderLineRepository.save(line);

        purchaseOrderService.receiveLine(line.getId(), wh.getId(), null, new BigDecimal("4"), new BigDecimal("8.00"));

        InventoryLedger recv = ledgerRepository
                .findByTenantIdAndReferenceTypeAndReferenceId(tenantId, "PURCHASE_ORDER_LINE", line.getId())
                .stream()
                .findFirst()
                .orElseThrow();
        // 5 + 8/4 = 7
        assertThat(recv.getUnitCost()).isEqualByComparingTo("7.000000");
    }
}
