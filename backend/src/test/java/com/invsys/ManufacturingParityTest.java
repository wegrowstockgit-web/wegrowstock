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
import com.invsys.domain.ProductionTimesheet;
import com.invsys.domain.SalesOrder;
import com.invsys.domain.SalesOrderLine;
import com.invsys.domain.TeamLaborRate;
import com.invsys.repository.BomOperationRepository;
import com.invsys.repository.BomRepository;
import com.invsys.repository.CustomerRepository;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ManufacturingOperationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.repository.ProductionTimesheetRepository;
import com.invsys.repository.SalesOrderLineRepository;
import com.invsys.repository.SalesOrderRepository;
import com.invsys.repository.TeamLaborRateRepository;
import com.invsys.repository.UserRepository;
import com.invsys.domain.User;
import com.invsys.service.InventoryService;
import com.invsys.service.KitService;
import com.invsys.service.ManufacturingLaborService;
import com.invsys.service.ManufacturingService;
import com.invsys.service.SalesOrderService;
import com.invsys.service.ShipmentService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManufacturingParityTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SalesOrderRepository salesOrderRepository;
    @Autowired SalesOrderLineRepository salesOrderLineRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired InventoryService inventoryService;
    @Autowired ManufacturingService manufacturingService;
    @Autowired ManufacturingLaborService laborService;
    @Autowired ManufacturingOperationRepository operationRepository;
    @Autowired BomRepository bomRepository;
    @Autowired BomOperationRepository bomOperationRepository;
    @Autowired TeamLaborRateRepository laborRateRepository;
    @Autowired UserRepository userRepository;
    @Autowired ProductionTimesheetRepository timesheetRepository;
    @Autowired KitService kitService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired ShipmentService shipmentService;

    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void executeAssemblyRollsLaborCostIntoFinishedGoodAvgCost() {
        UUID tenantId = setupTenant("labor-");
        User user = createUser(tenantId, "labor@test.local");
        TenantContext.setUserId(user.getId());

        Product parentProduct = saveProduct(tenantId, "LAB", "Labor Assembly");
        Product compProduct = saveProduct(tenantId, "LCP", "Labor Component");
        ProductVariant parent = saveVariant(tenantId, parentProduct.getId(), "LAB-1");
        ProductVariant component = saveVariant(tenantId, compProduct.getId(), "LCP-1");
        Location location = saveLocation(tenantId, "WH-LAB", "/WH-LAB");

        ManufacturingOperation operation = new ManufacturingOperation();
        operation.setTenantId(tenantId);
        operation.setName("Weld");
        operation.setDefaultHourlyRate(new BigDecimal("40.00"));
        operation = operationRepository.save(operation);

        Bom bom = manufacturingService.createBom(parent.getId(), "Labor BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)));

        BomOperation bomOp = new BomOperation();
        bomOp.setTenantId(tenantId);
        bomOp.setBomId(bom.getId());
        bomOp.setOperationId(operation.getId());
        bomOp.setEstimatedHours(new BigDecimal("1"));
        bomOperationRepository.save(bomOp);

        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("10"),
                null, null, new BigDecimal("5.00"));

        ProductionOrder order = manufacturingService.createProductionOrder(parent.getId(), BigDecimal.ONE);
        manufacturingService.allocateComponents(order.getId());

        ProductionTimesheet timesheet = laborService.startTimesheet(order.getId(), operation.getId());
        timesheet.setStartTime(Instant.now().minusSeconds(3600));
        timesheetRepository.save(timesheet);
        laborService.stopTimesheet(timesheet.getId());

        manufacturingService.executeAssembly(order.getId(), BigDecimal.ONE);

        ProductVariant finished = variantRepository.findById(parent.getId()).orElseThrow();
        assertThat(finished.getAvgCost()).isGreaterThan(new BigDecimal("40.00"));

        List<InventoryLedger> assemblyIn = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(
                tenantId, parent.getId());
        assertThat(assemblyIn.stream().filter(l -> "ASSEMBLY_IN".equals(l.getMovementType())).findFirst())
                .isPresent()
                .get()
                .extracting(InventoryLedger::getUnitCost)
                .isEqualTo(finished.getAvgCost());
    }

    @Test
    void disassembleSplitsFinishedGoodIntoComponents() {
        UUID tenantId = setupTenant("dis-");

        Product parentProduct = saveProduct(tenantId, "DIS", "Disassembly Parent");
        Product compProduct = saveProduct(tenantId, "DSC", "Disassembly Component");
        ProductVariant parent = saveVariant(tenantId, parentProduct.getId(), "DIS-1");
        ProductVariant component = saveVariant(tenantId, compProduct.getId(), "DSC-1");
        Location location = saveLocation(tenantId, "WH-DIS", "/WH-DIS");

        manufacturingService.createBom(parent.getId(), "Dis BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), new BigDecimal("3"))));

        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("30"), null, null);
        ProductionOrder order = manufacturingService.createProductionOrder(parent.getId(), new BigDecimal("2"));
        manufacturingService.allocateComponents(order.getId());
        manufacturingService.executeAssembly(order.getId(), new BigDecimal("2"));

        manufacturingService.disassemble(parent.getId(), location.getId(), BigDecimal.ONE);

        BigDecimal componentIn = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, component.getId())
                .stream()
                .filter(l -> "ASSEMBLY_IN".equals(l.getMovementType()) && "DISASSEMBLY".equals(l.getReferenceType()))
                .map(InventoryLedger::getQuantityDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(componentIn).isEqualByComparingTo(new BigDecimal("3"));
    }

    @Test
    void autoAssembleBomAllocatesAndShipsComponents() {
        UUID tenantId = setupTenant("auto-");

        Product kitProduct = saveProduct(tenantId, "AUT", "Auto Kit");
        Product compProduct = saveProduct(tenantId, "ACM", "Auto Component");
        ProductVariant kitVariant = saveVariant(tenantId, kitProduct.getId(), "AUT-1");
        ProductVariant compVariant = saveVariant(tenantId, compProduct.getId(), "ACM-1");
        Location location = saveLocation(tenantId, "WH-AUT", "/WH-AUT");

        Bom bom = manufacturingService.createBom(kitVariant.getId(), "Auto BOM",
                List.of(new ManufacturingService.BomLineInput(compVariant.getId(), new BigDecimal("2"))), true);
        assertThat(bom.isAutoAssemble()).isTrue();
        assertThat(kitService.isKit(kitVariant.getId())).isTrue();

        inventoryService.receive(compVariant.getId(), location.getId(), null, new BigDecimal("20"), null, null);

        Customer customer = new Customer();
        customer.setTenantId(tenantId);
        customer.setName("Auto Customer");
        customer = customerRepository.save(customer);

        SalesOrder order = new SalesOrder();
        order.setTenantId(tenantId);
        order.setCustomerId(customer.getId());
        order.setNumber("SO-AUTO-1");
        order.setStatus("CONFIRMED");
        order = salesOrderRepository.save(order);

        SalesOrderLine line = new SalesOrderLine();
        line.setTenantId(tenantId);
        line.setSalesOrderId(order.getId());
        line.setVariantId(kitVariant.getId());
        line.setQtyOrdered(new BigDecimal("2"));
        line = salesOrderLineRepository.save(line);

        salesOrderService.allocate(order.getId());
        shipmentService.createShipment(order.getId(), "UPS", "1ZAUTO", List.of(
                new ShipmentService.ShipLineRequest(line.getId(), new BigDecimal("2"))));

        BigDecimal shipped = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(tenantId, compVariant.getId())
                .stream()
                .filter(l -> "SHIP".equals(l.getMovementType()))
                .map(InventoryLedger::getQuantityDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shipped).isEqualByComparingTo(new BigDecimal("-4"));
    }

    @Test
    void partialAssemblyLeavesOrderInWip() {
        UUID tenantId = setupTenant("wip-");

        Product parentProduct = saveProduct(tenantId, "WIP", "WIP Parent");
        Product compProduct = saveProduct(tenantId, "WCP", "WIP Component");
        ProductVariant parent = saveVariant(tenantId, parentProduct.getId(), "WIP-1");
        ProductVariant component = saveVariant(tenantId, compProduct.getId(), "WCP-1");
        Location location = saveLocation(tenantId, "WH-WIP", "/WH-WIP");

        manufacturingService.createBom(parent.getId(), "WIP BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)));
        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("10"), null, null);

        ProductionOrder order = manufacturingService.createProductionOrder(parent.getId(), new BigDecimal("3"));
        manufacturingService.allocateComponents(order.getId());
        ProductionOrder wip = manufacturingService.executeAssembly(order.getId(), BigDecimal.ONE);

        assertThat(wip.getStatus()).isEqualTo("WIP");
        assertThat(wip.getQtyProduced()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void addBomLineAndDuplicateBomGuardrails() {
        UUID tenantId = setupTenant("bom-");

        Product product = saveProduct(tenantId, "BOM", "BOM Product");
        ProductVariant parent = saveVariant(tenantId, product.getId(), "BOM-P");
        ProductVariant componentA = saveVariant(tenantId, product.getId(), "BOM-A");
        ProductVariant componentB = saveVariant(tenantId, product.getId(), "BOM-B");

        Bom bom = manufacturingService.createBom(parent.getId(), "Extend BOM",
                List.of(new ManufacturingService.BomLineInput(componentA.getId(), BigDecimal.ONE)));

        var addedLine = manufacturingService.addBomLine(bom.getId(), componentB.getId(), new BigDecimal("2"));
        assertThat(addedLine.getComponentVariantId()).isEqualTo(componentB.getId());

        assertThatThrownBy(() -> manufacturingService.createBom(parent.getId(), "Duplicate",
                List.of(new ManufacturingService.BomLineInput(componentA.getId(), BigDecimal.ONE))))
                .isInstanceOf(com.invsys.common.ApiException.class);
    }

    @Test
    void teamLaborRateOverridesOperationDefaultOnTimesheet() {
        UUID tenantId = setupTenant("rate-");
        User user = createUser(tenantId, "rate@test.local");
        TenantContext.setUserId(user.getId());

        TeamLaborRate rate = new TeamLaborRate();
        rate.setTenantId(tenantId);
        rate.setUserId(user.getId());
        rate.setHourlyRate(new BigDecimal("55.00"));
        laborRateRepository.save(rate);

        ManufacturingOperation operation = new ManufacturingOperation();
        operation.setTenantId(tenantId);
        operation.setName("Pack");
        operation.setDefaultHourlyRate(new BigDecimal("20.00"));
        operation = operationRepository.save(operation);

        Product product = saveProduct(tenantId, "TS", "Timesheet Product");
        ProductVariant parent = saveVariant(tenantId, product.getId(), "TS-1");
        ProductVariant component = saveVariant(tenantId, product.getId(), "TS-C");
        Location location = saveLocation(tenantId, "WH-TS", "/WH-TS");

        manufacturingService.createBom(parent.getId(), "TS BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)));
        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("5"), null, null);

        ProductionOrder order = manufacturingService.createProductionOrder(parent.getId(), BigDecimal.ONE);
        manufacturingService.allocateComponents(order.getId());

        ProductionTimesheet timesheet = laborService.startTimesheet(order.getId(), operation.getId());
        timesheet.setStartTime(Instant.now().minusSeconds(3600));
        timesheetRepository.save(timesheet);
        ProductionTimesheet stopped = laborService.stopTimesheet(timesheet.getId());

        assertThat(stopped.getTotalCost()).isEqualByComparingTo(new BigDecimal("55.00"));
    }

    private UUID setupTenant(String prefix) {
        UUID tenantId = testDataHelper.createTenant("Mfg Parity", prefix + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);
        return tenantId;
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

    private User createUser(UUID tenantId, String email) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setDisplayName("Test Worker");
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }
}
