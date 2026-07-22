package com.invsys;

import com.invsys.domain.Bom;
import com.invsys.domain.BomOperation;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.domain.ManufacturingOperation;
import com.invsys.domain.ManufacturingWorkCenter;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.ProductionOrder;
import com.invsys.repository.BomOperationRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.repository.ManufacturingOperationRepository;
import com.invsys.repository.ManufacturingWorkCenterRepository;
import com.invsys.modules.catalog.repository.ProductRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.service.ManufacturingService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.invsys.core.common.ApiException;

class WorkCenterRoutingTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired ManufacturingWorkCenterRepository workCenterRepository;
    @Autowired ManufacturingOperationRepository manufacturingOperationRepository;
    @Autowired BomOperationRepository bomOperationRepository;
    @Autowired ManufacturingService manufacturingService;
    @Autowired InventoryService inventoryService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void assemblyAssignsWorkCenterAndAdvanceMovesSequence() {
        UUID tenantId = testDataHelper.createTenant("WC Route", "wcr-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product parentProduct = new Product();
        parentProduct.setTenantId(tenantId);
        parentProduct.setSkuRoot("FG");
        parentProduct.setName("Finished Good");
        parentProduct = productRepository.save(parentProduct);

        Product compProduct = new Product();
        compProduct.setTenantId(tenantId);
        compProduct.setSkuRoot("RM");
        compProduct.setName("Raw Material");
        compProduct = productRepository.save(compProduct);

        ProductVariant parent = new ProductVariant();
        parent.setTenantId(tenantId);
        parent.setProductId(parentProduct.getId());
        parent.setSku("FG-1");
        parent = variantRepository.save(parent);

        ProductVariant component = new ProductVariant();
        component.setTenantId(tenantId);
        component.setProductId(compProduct.getId());
        component.setSku("RM-1");
        component = variantRepository.save(component);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("BIN");
        location.setCode("BIN-WC");
        location.setName("WC Bin");
        location.setPath("/BIN-WC");
        location = locationRepository.save(location);

        ManufacturingWorkCenter wc1 = new ManufacturingWorkCenter();
        wc1.setTenantId(tenantId);
        wc1.setCode("WC-CUT");
        wc1.setName("Cutting");
        wc1.setOperationalStatus("ACTIVE");
        wc1 = workCenterRepository.save(wc1);

        ManufacturingWorkCenter wc2 = new ManufacturingWorkCenter();
        wc2.setTenantId(tenantId);
        wc2.setCode("WC-ASM");
        wc2.setName("Assembly");
        wc2.setOperationalStatus("ACTIVE");
        wc2 = workCenterRepository.save(wc2);

        ManufacturingOperation op1 = new ManufacturingOperation();
        op1.setTenantId(tenantId);
        op1.setName("Cut");
        op1 = manufacturingOperationRepository.save(op1);

        ManufacturingOperation op2 = new ManufacturingOperation();
        op2.setTenantId(tenantId);
        op2.setName("Assemble");
        op2 = manufacturingOperationRepository.save(op2);

        Bom bom = manufacturingService.createBom(parent.getId(), "FG BOM", List.of(
                new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)));

        BomOperation route1 = new BomOperation();
        route1.setTenantId(tenantId);
        route1.setBomId(bom.getId());
        route1.setOperationId(op1.getId());
        route1.setSequenceOrder(10);
        route1.setWorkCenterId(wc1.getId());
        route1.setEstimatedHours(BigDecimal.ONE);
        bomOperationRepository.save(route1);

        BomOperation route2 = new BomOperation();
        route2.setTenantId(tenantId);
        route2.setBomId(bom.getId());
        route2.setOperationId(op2.getId());
        route2.setSequenceOrder(20);
        route2.setWorkCenterId(wc2.getId());
        route2.setEstimatedHours(BigDecimal.ONE);
        bomOperationRepository.save(route2);

        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("20"), null, null);

        ProductionOrder order = manufacturingService.createProductionOrder(parent.getId(), new BigDecimal("10"));
        manufacturingService.allocateComponents(order.getId());
        order = manufacturingService.executeAssembly(order.getId(), new BigDecimal("4"));

        assertThat(order.getStatus()).isIn("WIP", "IN_ROUTING");
        assertThat(order.getCurrentWorkCenterId()).isEqualTo(wc1.getId());
        assertThat(order.getQtyProduced()).isEqualByComparingTo("4");

        order = manufacturingService.advanceWorkCenter(order.getId());
        assertThat(order.getCurrentWorkCenterId()).isEqualTo(wc2.getId());
        assertThat(order.getStatus()).isEqualTo("IN_ROUTING");

        order = manufacturingService.executeAssembly(order.getId(), new BigDecimal("6"));
        assertThat(order.getStatus()).isEqualTo("COMPLETED");
        assertThat(order.getQtyProduced()).isEqualByComparingTo("10");

        ProductionOrder finalOrder = order;
        assertThatThrownBy(() -> manufacturingService.advanceWorkCenter(finalOrder.getId()))
                .isInstanceOf(com.invsys.core.common.ApiException.class)
                .satisfies(ex -> assertThat(((com.invsys.core.common.ApiException) ex).getCode())
                        .isIn("INVALID_STATE", "ROUTING_COMPLETE"));
    }

    @Test
    void advanceWithoutRoutingFails() {
        UUID tenantId = testDataHelper.createTenant("WC None", "wcn-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product parentProduct = new Product();
        parentProduct.setTenantId(tenantId);
        parentProduct.setSkuRoot("FG2");
        parentProduct.setName("Finished Good 2");
        parentProduct = productRepository.save(parentProduct);

        Product compProduct = new Product();
        compProduct.setTenantId(tenantId);
        compProduct.setSkuRoot("RM2");
        compProduct.setName("Raw 2");
        compProduct = productRepository.save(compProduct);

        ProductVariant parent = new ProductVariant();
        parent.setTenantId(tenantId);
        parent.setProductId(parentProduct.getId());
        parent.setSku("FG-2");
        parent = variantRepository.save(parent);

        ProductVariant component = new ProductVariant();
        component.setTenantId(tenantId);
        component.setProductId(compProduct.getId());
        component.setSku("RM-2");
        component = variantRepository.save(component);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("BIN");
        location.setCode("BIN-WCN");
        location.setName("WC Bin 2");
        location.setPath("/BIN-WCN");
        location = locationRepository.save(location);

        manufacturingService.createBom(parent.getId(), "FG BOM 2", List.of(
                new ManufacturingService.BomLineInput(component.getId(), BigDecimal.ONE)));
        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("5"), null, null);

        ProductionOrder order = manufacturingService.createProductionOrder(parent.getId(), new BigDecimal("2"));
        manufacturingService.allocateComponents(order.getId());
        ProductionOrder assembled = manufacturingService.executeAssembly(order.getId(), new BigDecimal("1"));

        assertThatThrownBy(() -> manufacturingService.advanceWorkCenter(assembled.getId()))
                .isInstanceOf(com.invsys.core.common.ApiException.class)
                .satisfies(ex -> assertThat(((com.invsys.core.common.ApiException) ex).getCode()).isEqualTo("NO_ROUTING"));
    }
}
