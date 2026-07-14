package com.invsys;

import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductCategory;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.domain.SupplierInvoiceIngestion;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductCategoryRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SupplierInvoiceIngestionRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.service.LandedCostService;
import com.invsys.service.PurchaseOrderService;
import com.invsys.service.landedcost.HybridLandedCostEngine;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LandedCostServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired SupplierRepository supplierRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired SupplierInvoiceIngestionRepository ingestionRepository;
    @Autowired LandedCostService landedCostService;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired ProductCategoryRepository productCategoryRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void allocatesFreightByValueWithoutChangingOnHand() {
        UUID tenantId = testDataHelper.createTenant("Landed Co", "land-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Freight Vendor");
        supplier = supplierRepository.save(supplier);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("LC");
        product.setName("Landed SKU");
        product = productRepository.save(product);

        ProductVariant a = new ProductVariant();
        a.setTenantId(tenantId);
        a.setProductId(product.getId());
        a.setSku("LC-A");
        a.setWeight(new BigDecimal("1"));
        a = variantRepository.save(a);

        ProductVariant b = new ProductVariant();
        b.setTenantId(tenantId);
        b.setProductId(product.getId());
        b.setSku("LC-B");
        b.setWeight(new BigDecimal("1"));
        b = variantRepository.save(b);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-LC");
        wh.setName("Landed WH");
        wh.setPath("/WH-LC");
        wh = locationRepository.save(wh);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-LC-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine lineA = new PurchaseOrderLine();
        lineA.setTenantId(tenantId);
        lineA.setPurchaseOrderId(po.getId());
        lineA.setVariantId(a.getId());
        lineA.setQtyOrdered(new BigDecimal("10"));
        lineA.setUnitCost(new BigDecimal("10.00"));
        lineA = purchaseOrderLineRepository.save(lineA);

        PurchaseOrderLine lineB = new PurchaseOrderLine();
        lineB.setTenantId(tenantId);
        lineB.setPurchaseOrderId(po.getId());
        lineB.setVariantId(b.getId());
        lineB.setQtyOrdered(new BigDecimal("10"));
        lineB.setUnitCost(new BigDecimal("30.00"));
        lineB = purchaseOrderLineRepository.save(lineB);

        purchaseOrderService.receiveLine(lineA.getId(), wh.getId(), null, new BigDecimal("10"));
        purchaseOrderService.receiveLine(lineB.getId(), wh.getId(), null, new BigDecimal("10"));

        SupplierInvoiceIngestion invoice = new SupplierInvoiceIngestion();
        invoice.setTenantId(tenantId);
        invoice.setPurchaseOrderId(po.getId());
        invoice.setStatus("RECONCILED");
        invoice.setExtractedData(new LinkedHashMap<>(Map.of("freight", 100)));
        invoice = ingestionRepository.save(invoice);

        BigDecimal avgABefore = variantRepository.findById(a.getId()).orElseThrow().getAvgCost();
        BigDecimal avgBBefore = variantRepository.findById(b.getId()).orElseThrow().getAvgCost();

        LandedCostService.LandedCostResult result = landedCostService.allocate(
                invoice.getId(),
                new BigDecimal("100.00"),
                LandedCostService.AllocationStrategy.BY_VALUE);

        assertThat(result.strategy()).isEqualTo("CUSTOMS");
        assertThat(result.lines()).hasSize(2);
        // Value basis: A=100, B=300 â†’ 25% / 75% of freight
        assertThat(new BigDecimal(result.lines().get(0).get("allocatedFreight").toString())
                .add(new BigDecimal(result.lines().get(1).get("allocatedFreight").toString())))
                .isEqualByComparingTo("100.00");

        List<InventoryLedger> costLines = ledgerRepository.findAll().stream()
                .filter(e -> "LANDED_COST_ALLOCATION".equals(e.getReasonCode()))
                .toList();
        assertThat(costLines).hasSize(2);
        assertThat(costLines).allMatch(e -> e.getQuantityDelta().compareTo(BigDecimal.ZERO) == 0);
        assertThat(costLines).allMatch(e -> "ADJUST".equals(e.getMovementType()));
        assertThat(costLines).allMatch(e -> e.getLandedCostComponent() != null
                && e.getLandedCostComponent().signum() > 0);
        assertThat(costLines.stream()
                .map(InventoryLedger::getLandedCostComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");

        BigDecimal avgAAfter = variantRepository.findById(a.getId()).orElseThrow().getAvgCost();
        BigDecimal avgBAfter = variantRepository.findById(b.getId()).orElseThrow().getAvgCost();
        assertThat(avgAAfter).isGreaterThan(avgABefore);
        assertThat(avgBAfter).isGreaterThan(avgBBefore);
        assertThat(avgBAfter.subtract(avgBBefore)).isGreaterThan(avgAAfter.subtract(avgABefore));
    }

    @Test
    void allocatesFreightByVolumeWithCategoryMedianCascade() {
        UUID tenantId = testDataHelper.createTenant("Vol LC", "vollc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Volume Vendor");
        supplier = supplierRepository.save(supplier);

        ProductCategory category = new ProductCategory();
        category.setTenantId(tenantId);
        category.setName("Crates");
        category.setMedianVolume(new BigDecimal("2.0"));
        category = productCategoryRepository.save(category);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("VOL");
        product.setName("Volume SKU");
        product = productRepository.save(product);

        ProductVariant withVolume = new ProductVariant();
        withVolume.setTenantId(tenantId);
        withVolume.setProductId(product.getId());
        withVolume.setSku("VOL-A");
        withVolume.setVolume(new BigDecimal("4.0"));
        withVolume = variantRepository.save(withVolume);

        ProductVariant medianOnly = new ProductVariant();
        medianOnly.setTenantId(tenantId);
        medianOnly.setProductId(product.getId());
        medianOnly.setSku("VOL-B");
        medianOnly.setCategoryId(category.getId());
        medianOnly = variantRepository.save(medianOnly);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-VOL");
        wh.setName("Volume WH");
        wh.setPath("/WH-VOL");
        wh = locationRepository.save(wh);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-VOL-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine lineA = poLine(tenantId, po.getId(), withVolume.getId());
        PurchaseOrderLine lineB = poLine(tenantId, po.getId(), medianOnly.getId());
        purchaseOrderService.receiveLine(lineA.getId(), wh.getId(), null, new BigDecimal("10"));
        purchaseOrderService.receiveLine(lineB.getId(), wh.getId(), null, new BigDecimal("10"));

        SupplierInvoiceIngestion invoice = new SupplierInvoiceIngestion();
        invoice.setTenantId(tenantId);
        invoice.setPurchaseOrderId(po.getId());
        invoice.setStatus("RECONCILED");
        invoice.setExtractedData(new LinkedHashMap<>());
        invoice = ingestionRepository.save(invoice);

        LandedCostService.LandedCostResult result = landedCostService.allocate(
                invoice.getId(),
                new BigDecimal("60.00"),
                HybridLandedCostEngine.CostEventType.FREIGHT,
                "BY_VOLUME");

        assertThat(result.strategy()).isEqualTo("BY_VOLUME");
        assertThat(result.lines()).hasSize(2);
        // Volume 4 vs median 2 â†’ 40 / 20 of 60
        Map<String, BigDecimal> bySku = new LinkedHashMap<>();
        for (Map<String, Object> row : result.lines()) {
            bySku.put(row.get("sku").toString(), new BigDecimal(row.get("allocatedFreight").toString()));
        }
        assertThat(bySku.get("VOL-A")).isEqualByComparingTo("40.00");
        assertThat(bySku.get("VOL-B")).isEqualByComparingTo("20.00");

        List<InventoryLedger> costLines = ledgerRepository.findAll().stream()
                .filter(e -> "LANDED_COST_ALLOCATION".equals(e.getReasonCode()))
                .toList();
        assertThat(costLines).hasSize(2);
        assertThat(costLines).allMatch(e -> e.getLandedCostComponent().signum() > 0);
        assertThat(costLines.stream().map(InventoryLedger::getLandedCostComponent)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void hybridFreightUsesCategoryMedianAndNeverZeroAllocates() {
        UUID tenantId = testDataHelper.createTenant("Hybrid LC", "hyblc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("Hybrid Vendor");
        supplier = supplierRepository.save(supplier);

        ProductCategory category = new ProductCategory();
        category.setTenantId(tenantId);
        category.setName("Bulky");
        category.setMedianWeight(new BigDecimal("3.0"));
        category = productCategoryRepository.save(category);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("HY");
        product.setName("Hybrid SKU");
        product = productRepository.save(product);

        ProductVariant weighed = new ProductVariant();
        weighed.setTenantId(tenantId);
        weighed.setProductId(product.getId());
        weighed.setSku("HY-W");
        weighed.setWeight(new BigDecimal("6.0"));
        weighed = variantRepository.save(weighed);

        ProductVariant categoryOnly = new ProductVariant();
        categoryOnly.setTenantId(tenantId);
        categoryOnly.setProductId(product.getId());
        categoryOnly.setSku("HY-C");
        categoryOnly.setCategoryId(category.getId());
        categoryOnly = variantRepository.save(categoryOnly);

        ProductVariant bare = new ProductVariant();
        bare.setTenantId(tenantId);
        bare.setProductId(product.getId());
        bare.setSku("HY-Q");
        bare = variantRepository.save(bare);

        Location wh = new Location();
        wh.setTenantId(tenantId);
        wh.setType("WAREHOUSE");
        wh.setCode("WH-HY");
        wh.setName("Hybrid WH");
        wh.setPath("/WH-HY");
        wh = locationRepository.save(wh);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-HY-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine l1 = poLine(tenantId, po.getId(), weighed.getId());
        PurchaseOrderLine l2 = poLine(tenantId, po.getId(), categoryOnly.getId());
        PurchaseOrderLine l3 = poLine(tenantId, po.getId(), bare.getId());
        purchaseOrderService.receiveLine(l1.getId(), wh.getId(), null, new BigDecimal("10"));
        purchaseOrderService.receiveLine(l2.getId(), wh.getId(), null, new BigDecimal("10"));
        purchaseOrderService.receiveLine(l3.getId(), wh.getId(), null, new BigDecimal("10"));

        SupplierInvoiceIngestion invoice = new SupplierInvoiceIngestion();
        invoice.setTenantId(tenantId);
        invoice.setPurchaseOrderId(po.getId());
        invoice.setStatus("RECONCILED");
        invoice.setExtractedData(new LinkedHashMap<>());
        invoice = ingestionRepository.save(invoice);

        LandedCostService.LandedCostResult result = landedCostService.allocate(
                invoice.getId(),
                new BigDecimal("90.00"),
                HybridLandedCostEngine.CostEventType.FREIGHT,
                "HYBRID");

        assertThat(result.strategy()).isEqualTo("HYBRID");
        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines())
                .allMatch(row -> new BigDecimal(row.get("allocatedFreight").toString()).signum() > 0);
        BigDecimal sum = result.lines().stream()
                .map(row -> new BigDecimal(row.get("allocatedFreight").toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("90.00");
    }

    private PurchaseOrderLine poLine(UUID tenantId, UUID poId, UUID variantId) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(poId);
        line.setVariantId(variantId);
        line.setQtyOrdered(new BigDecimal("10"));
        line.setUnitCost(new BigDecimal("8.00"));
        return purchaseOrderLineRepository.save(line);
    }
}
