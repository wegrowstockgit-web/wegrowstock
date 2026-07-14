package com.invsys;

import com.invsys.domain.Bom;
import com.invsys.domain.BomOperation;
import com.invsys.domain.Customer;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.ManufacturingOperation;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.ProductionOrder;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.User;
import com.invsys.repository.BomOperationRepository;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ManufacturingOperationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.UserRepository;
import com.invsys.service.InventoryService;
import com.invsys.service.ManufacturingDtoMapper;
import com.invsys.service.ManufacturingLaborService;
import com.invsys.service.ManufacturingService;
import com.invsys.service.SalesOrderService;
import com.invsys.service.ShipmentService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CoreServiceCoverageTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired InventoryService inventoryService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired ShipmentService shipmentService;
    @Autowired ManufacturingService manufacturingService;
    @Autowired ManufacturingDtoMapper dtoMapper;
    @Autowired ManufacturingLaborService laborService;
    @Autowired ManufacturingOperationRepository operationRepository;
    @Autowired BomOperationRepository bomOperationRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void salesOrderConfirmAllocateCancelLifecycle() {
        UUID tenantId = testDataHelper.createTenant("SO Cov", "socov-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = saveProduct(tenantId, "SOC", "SO Coverage");
        ProductVariant variant = saveVariant(tenantId, product.getId(), "SOC-1");
        Location location = saveLocation(tenantId, "WH-SOC", "/WH-SOC");

        inventoryService.receive(variant.getId(), location.getId(), null, new BigDecimal("10"), null, null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("SO Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-COV-1");
        order.setStatus("DRAFT");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("3"));
        salesOrderLineRepository.save(line);

        SalesOrder confirmed = salesOrderService.confirm(order.getId());
        assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");

        SalesOrder allocated = salesOrderService.allocate(order.getId());
        assertThat(allocated.getStatus()).isEqualTo("ALLOCATED");

        SalesOrder cancelled = salesOrderService.cancel(order.getId());
        assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void inventoryAdjustAndTransferWriteLedgerMovements() {
        UUID tenantId = testDataHelper.createTenant("Inv Cov", "invcov-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = saveProduct(tenantId, "INV", "Inventory Coverage");
        ProductVariant variant = saveVariant(tenantId, product.getId(), "INV-1");
        Location from = saveLocation(tenantId, "WH-A", "/WH-A");
        Location to = saveLocation(tenantId, "WH-B", "/WH-B");

        inventoryService.receive(variant.getId(), from.getId(), null, new BigDecimal("8"), null, null);
        inventoryService.adjust(variant.getId(), from.getId(), null, new BigDecimal("-1"), "CYCLE_COUNT");
        inventoryService.transfer(variant.getId(), from.getId(), to.getId(), null, new BigDecimal("2"));

        List<InventoryLedger> movements = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(
                tenantId, variant.getId());
        assertThat(movements.stream().anyMatch(l -> "ADJUST".equals(l.getMovementType()))).isTrue();
        assertThat(movements.stream().filter(l -> "TRANSFER_OUT".equals(l.getMovementType())).count()).isEqualTo(1);
        assertThat(movements.stream().filter(l -> "TRANSFER_IN".equals(l.getMovementType())).count()).isEqualTo(1);
    }

    @Test
    void manufacturingDtoMapperAndLaborListsExposeBomOperations() {
        UUID tenantId = testDataHelper.createTenant("Dto Cov", "dtocov-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        User worker = new User();
        worker.setTenantId(tenantId);
        worker.setEmail("dto@" + UUID.randomUUID() + ".test");
        worker.setPasswordHash(passwordEncoder.encode("password123"));
        worker.setDisplayName("DTO Worker");
        worker = userRepository.save(worker);
        TenantContext.setUserId(worker.getId());

        Product product = saveProduct(tenantId, "DTO", "DTO Product");
        ProductVariant parent = saveVariant(tenantId, product.getId(), "DTO-P");
        ProductVariant component = saveVariant(tenantId, product.getId(), "DTO-C");
        Location location = saveLocation(tenantId, "WH-DTO", "/WH-DTO");

        Bom bom = manufacturingService.createBom(parent.getId(), "DTO BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)), true);

        ManufacturingOperation operation = new ManufacturingOperation();
        operation.setTenantId(tenantId);
        operation.setName("Calibrate");
        operation.setDefaultHourlyRate(new BigDecimal("45"));
        operation = operationRepository.save(operation);

        BomOperation bomOp = new BomOperation();
        bomOp.setTenantId(tenantId);
        bomOp.setBomId(bom.getId());
        bomOp.setOperationId(operation.getId());
        bomOp.setEstimatedHours(BigDecimal.ONE);
        bomOperationRepository.save(bomOp);

        var bomResponse = dtoMapper.toBomResponse(bom);
        assertThat(bomResponse.autoAssemble()).isTrue();
        assertThat(bomResponse.lines()).hasSize(1);
        assertThat(bomResponse.lines().getFirst().componentSku()).isEqualTo("DTO-C");

        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("5"), null, null);
        ProductionOrder order = manufacturingService.createProductionOrder(parent.getId(), BigDecimal.ONE);
        ProductionOrder allocated = manufacturingService.allocateComponents(order.getId());

        var orderResponse = dtoMapper.toProductionOrderResponse(allocated);
        assertThat(orderResponse.parentSku()).isEqualTo("DTO-P");
        assertThat(orderResponse.status()).isEqualTo("COMPONENTS_ALLOCATED");

        assertThat(laborService.listOperationsForOrder(allocated.getId())).hasSize(1);
        laborService.startTimesheet(allocated.getId(), operation.getId());
        assertThat(laborService.listTimesheets(allocated.getId())).hasSize(1);
        assertThat(laborService.totalLaborCost(allocated.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void standardSalesOrderShipsViaShipmentService() {
        UUID tenantId = testDataHelper.createTenant("Ship Cov", "shipcov-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = saveProduct(tenantId, "SHP", "Ship Coverage");
        ProductVariant variant = saveVariant(tenantId, product.getId(), "SHP-1");
        Location location = saveLocation(tenantId, "WH-SHP", "/WH-SHP");

        inventoryService.receive(variant.getId(), location.getId(), null, new BigDecimal("12"), null, null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Ship Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-SHP-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(variant.getId());
        line.setQtyOrdered(new BigDecimal("4"));
        line = salesOrderLineRepository.save(line);

        salesOrderService.allocate(order.getId());
        shipmentService.createShipment(order.getId(), "UPS", "1ZSHIP", List.of(
                new ShipmentService.ShipLineRequest(line.getId(), new BigDecimal("4"))));

        BigDecimal shipped = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, variant.getId())
                .stream()
                .filter(l -> "SHIP".equals(l.getMovementType()))
                .map(InventoryLedger::getQuantityDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shipped).isEqualByComparingTo(new BigDecimal("-4"));
    }

    private Product saveProduct(UUID tenantId, String skuRoot, String name) {
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot(skuRoot);
        product.setName(name);
        return productRepository.save(product);
    }

    private ProductVariant saveVariant(UUID tenantId, UUID productId, String sku) {
        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(productId);
        variant.setSku(sku);
        return variantRepository.save(variant);
    }

    private Location saveLocation(UUID tenantId, String code, String path) {
        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode(code);
        location.setName(code);
        location.setPath(path);
        return locationRepository.save(location);
    }
}
