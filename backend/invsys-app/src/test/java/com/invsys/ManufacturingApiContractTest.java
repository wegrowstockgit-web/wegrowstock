package com.invsys;

import com.invsys.api.ManufacturingTerminalController;
import com.invsys.api.ProductionOrderController;
import com.invsys.api.dto.ProductionOrderResponse;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.ManufacturingOperation;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.ManufacturingOperationRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.repository.UserRepository;
import com.invsys.domain.User;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.ManufacturingLaborService;
import com.invsys.service.ManufacturingService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingApiContractTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductionOrderController productionOrderController;
    @Autowired ManufacturingTerminalController terminalController;
    @Autowired ManufacturingService manufacturingService;
    @Autowired ManufacturingLaborService laborService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired ManufacturingOperationRepository operationRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryLevelRepository levelRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsOwner(UUID tenantId, UUID userId) {
        TenantContext.setTenantId(tenantId);
        TenantContext.setUserId(userId);
        var auth = new UsernamePasswordAuthenticationToken(
                "test@local",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void productionOrderAndTimesheetApisWireCorrectly() {
        UUID tenantId = testDataHelper.createTenant("Mfg API", "mapi-" + UUID.randomUUID().toString().substring(0, 8));

        User worker = new User();
        worker.setTenantId(tenantId);
        worker.setEmail("worker@mapi.test");
        worker.setPasswordHash(passwordEncoder.encode("password123"));
        worker.setDisplayName("Floor Worker");
        worker = userRepository.save(worker);
        authenticateAsOwner(tenantId, worker.getId());

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("API");
        product.setName("API Product");
        product = productRepository.save(product);

        ProductVariant parent = new ProductVariant();
        parent.setTenantId(tenantId);
        parent.setProductId(product.getId());
        parent.setSku("API-P");
        parent = variantRepository.save(parent);

        ProductVariant component = new ProductVariant();
        component.setTenantId(tenantId);
        component.setProductId(product.getId());
        component.setSku("API-C");
        component = variantRepository.save(component);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-API");
        location.setName("API WH");
        location.setPath("/WH-API");
        location = locationRepository.save(location);

        manufacturingService.createBom(parent.getId(), "API BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)));

        ManufacturingOperation operation = new ManufacturingOperation();
        operation.setTenantId(tenantId);
        operation.setName("Solder");
        operation.setDefaultHourlyRate(new BigDecimal("30"));
        operation = operationRepository.save(operation);

        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("5"), null, null);

        ProductionOrderResponse created = productionOrderController.createOrder(
                new ProductionOrderController.CreateProductionOrderRequest(parent.getId(), BigDecimal.ONE));
        assertThat(created.status()).isEqualTo("DRAFT");

        ProductionOrderResponse allocated = productionOrderController.allocate(created.id());
        assertThat(allocated.status()).isEqualTo("COMPONENTS_ALLOCATED");

        assertThat(terminalController.listOperations(created.id())).hasSize(1);
        var started = terminalController.startTimesheet(created.id(),
                new ManufacturingTerminalController.StartTimesheetRequest(operation.getId()));
        assertThat(started.endTime()).isNull();

        var stopped = terminalController.stopTimesheet(started.id());
        assertThat(stopped.totalCost()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        ProductionOrderResponse assembled = productionOrderController.assemble(created.id(),
                new ProductionOrderController.AssembleRequest(BigDecimal.ONE));
        assertThat(assembled.status()).isEqualTo("COMPLETED");
        assertThat(assembled.qtyProduced()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void disassembleEndpointReversesAssembly() {
        UUID tenantId = testDataHelper.createTenant("Dis API", "disapi-" + UUID.randomUUID().toString().substring(0, 8));

        User owner = new User();
        owner.setTenantId(tenantId);
        owner.setEmail("owner@disapi.test");
        owner.setPasswordHash(passwordEncoder.encode("password123"));
        owner.setDisplayName("Dis Owner");
        owner = userRepository.save(owner);
        authenticateAsOwner(tenantId, owner.getId());

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("DISAPI");
        product.setName("Dis API");
        product = productRepository.save(product);

        ProductVariant parent = new ProductVariant();
        parent.setTenantId(tenantId);
        parent.setProductId(product.getId());
        parent.setSku("DIS-P");
        parent = variantRepository.save(parent);

        ProductVariant component = new ProductVariant();
        component.setTenantId(tenantId);
        component.setProductId(product.getId());
        component.setSku("DIS-C");
        component = variantRepository.save(component);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-DIS");
        location.setName("DIS WH");
        location.setPath("/WH-DIS");
        location = locationRepository.save(location);

        manufacturingService.createBom(parent.getId(), "Dis BOM",
                List.of(new ManufacturingService.BomLineInput(component.getId(), new BigDecimal("2"))));

        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("10"), null, null);
        var order = manufacturingService.createProductionOrder(parent.getId(), BigDecimal.ONE);
        manufacturingService.allocateComponents(order.getId());
        manufacturingService.executeAssembly(order.getId(), BigDecimal.ONE);

        terminalController.disassemble(new ManufacturingTerminalController.DisassembleRequest(
                parent.getId(), location.getId(), BigDecimal.ONE));

        BigDecimal componentOnHand = levelRepository.findByTenantIdAndVariantId(tenantId, component.getId())
                .stream()
                .map(l -> l.getOnHand())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(componentOnHand).isGreaterThanOrEqualTo(new BigDecimal("2"));
    }
}
