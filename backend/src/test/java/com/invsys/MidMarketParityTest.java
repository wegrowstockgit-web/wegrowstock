package com.invsys;

import com.invsys.api.dto.ScanLookupResponse;
import com.invsys.domain.Customer;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.PurchaseOrder;
import com.invsys.domain.PurchaseOrderLine;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.Supplier;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.PurchaseOrderLineRepository;
import com.invsys.repository.PurchaseOrderRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.SupplierRepository;
import com.invsys.service.InventoryService;
import com.invsys.service.ManufacturingService;
import com.invsys.service.PurchaseOrderService;
import com.invsys.service.SalesOrderService;
import com.invsys.service.ScanService;
import com.invsys.service.ShipmentService;
import com.invsys.service.UomConversionService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MidMarketParityTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired PurchaseOrderRepository purchaseOrderRepository;
    @Autowired PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired UomConversionService uomConversionService;
    @Autowired InventoryService inventoryService;
    @Autowired ScanService scanService;
    @Autowired ManufacturingService manufacturingService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired ShipmentService shipmentService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void receivingOneCaseAddsTwentyFourEaToLedger() {
        UUID tenantId = testDataHelper.createTenant("UoM Tenant", "uom-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = saveProduct(tenantId, "CASE", "Case Product");
        ProductVariant variant = saveVariant(tenantId, product.getId(), "CASE-1", "9900111122221");
        Location location = saveLocation(tenantId, "WH-UOM", "/WH-UOM");

        uomConversionService.saveForVariant(variant.getId(), List.of(
                new UomConversionService.UomConversionRequest("STANDARD", "EA", BigDecimal.ONE),
                new UomConversionService.UomConversionRequest("PURCHASING", "Case", new BigDecimal("24"))));

        Supplier supplier = new Supplier();
        supplier.setTenantId(tenantId);
        supplier.setName("UoM Supplier");
        supplier = supplierRepository.save(supplier);

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenantId);
        po.setSupplierId(supplier.getId());
        po.setNumber("PO-UOM-1");
        po.setStatus("SUBMITTED");
        po = purchaseOrderRepository.save(po);

        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setTenantId(tenantId);
        line.setPurchaseOrderId(po.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("10"));
        line.setQtyReceived(BigDecimal.ZERO);
        line.setUnitCost(new BigDecimal("5.00"));
        line = purchaseOrderLineRepository.save(line);

        purchaseOrderService.receiveLine(line.getId(), location.getId(), null, BigDecimal.ONE);

        List<InventoryLedger> receiveEntries = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(
                tenantId, variant.getId());
        assertThat(receiveEntries).isNotEmpty();
        assertThat(receiveEntries.getFirst().getMovementType()).isEqualTo("RECEIVE");
        assertThat(receiveEntries.getFirst().getQuantityDelta()).isEqualByComparingTo(new BigDecimal("24"));
    }

    @Test
    void scanReturnsDefaultPutawayLocationPath() {
        UUID tenantId = testDataHelper.createTenant("Putaway Tenant", "put-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = saveProduct(tenantId, "PUT", "Putaway Product");
        Location bin = saveLocation(tenantId, "BIN-A4", "WH-01/Z-A/A-4/B");
        ProductVariant variant = saveVariant(tenantId, product.getId(), "PUT-1", "9900222233331");
        variant.setDefaultLocationId(bin.getId());
        variantRepository.save(variant);

        ScanLookupResponse result = scanService.lookup("9900222233331");
        assertThat(result.defaultLocationId()).isEqualTo(bin.getId());
        assertThat(result.defaultLocationPath()).isEqualTo("WH-01/Z-A/A-4/B");
    }

    @Test
    void shippingKitDeductsComponentInventoryFromLedger() {
        UUID tenantId = testDataHelper.createTenant("Kit Tenant", "kit-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product kitProduct = saveProduct(tenantId, "KIT", "Starter Kit");
        Product compAProduct = saveProduct(tenantId, "CMPA", "Component A");
        Product compBProduct = saveProduct(tenantId, "CMPB", "Component B");

        ProductVariant kitVariant = saveVariant(tenantId, kitProduct.getId(), "KIT-1", null);
        kitVariant.setKit(true);
        kitVariant = variantRepository.save(kitVariant);

        ProductVariant compA = saveVariant(tenantId, compAProduct.getId(), "CMPA-1", null);
        ProductVariant compB = saveVariant(tenantId, compBProduct.getId(), "CMPB-1", null);
        Location location = saveLocation(tenantId, "WH-KIT", "/WH-KIT");

        manufacturingService.createBom(kitVariant.getId(), "Kit BOM", List.of(
                new ManufacturingService.BomLineInput(compA.getId(), new BigDecimal("2")),
                new ManufacturingService.BomLineInput(compB.getId(), new BigDecimal("3"))));

        inventoryService.receive(compA.getId(), location.getId(), null, new BigDecimal("50"), null, null);
        inventoryService.receive(compB.getId(), location.getId(), null, new BigDecimal("50"), null, null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Kit Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-KIT-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine soLine = new SalesOrderLine();
        soLine.setTenantId(tenantId);
        soLine.setSalesOrderId(order.getId());
        soLine.setVariantId(kitVariant.getId());
        soLine.setQtyOrdered(new BigDecimal("2"));
        soLine = salesOrderLineRepository.save(soLine);

        salesOrderService.allocate(order.getId());

        shipmentService.createShipment(order.getId(), "UPS", "1ZKITTEST", List.of(
                new ShipmentService.ShipLineRequest(soLine.getId(), new BigDecimal("2"))));

        BigDecimal compAShipped = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, compA.getId())
                .stream()
                .filter(e -> "SHIP".equals(e.getMovementType()))
                .map(InventoryLedger::getQuantityDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal compBShipped = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, compB.getId())
                .stream()
                .filter(e -> "SHIP".equals(e.getMovementType()))
                .map(InventoryLedger::getQuantityDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(compAShipped).isEqualByComparingTo(new BigDecimal("-4"));
        assertThat(compBShipped).isEqualByComparingTo(new BigDecimal("-6"));
    }

    private Product saveProduct(UUID tenantId, String skuRoot, String name) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(skuRoot);
        product.setName(name);
        return productRepository.save(product);
    }

    private ProductVariant saveVariant(UUID tenantId, UUID productId, String sku, String barcode) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku(sku);
        variant.setBarcode(barcode);
        return variantRepository.save(variant);
    }

    private Location saveLocation(UUID tenantId, String code, String path) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("BIN");
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        return locationRepository.save(location);
    }
}
