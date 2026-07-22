package com.invsys;

import com.invsys.domain.Bom;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.catalog.domain.Location;
import com.invsys.modules.catalog.domain.Product;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.ProductionOrder;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
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

class BomOutputsAssemblyTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;
    @Autowired InventoryService inventoryService;
    @Autowired ManufacturingService manufacturingService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void executeAssemblyPostsCoProductAndByProductAssemblyIn() {
        UUID tenantId = testDataHelper.createTenant("Process Mfg", "proc-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        ProductVariant main = saveVariant(tenantId, "MAIN-FG");
        ProductVariant co = saveVariant(tenantId, "CO-PROD");
        ProductVariant by = saveVariant(tenantId, "BY-PROD");
        ProductVariant component = saveVariant(tenantId, "RAW-1");

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-PROC");
        location.setName("Process WH");
        location.setPath("/WH-PROC");
        location = locationRepository.save(location);

        inventoryService.receive(component.getId(), location.getId(), null, new BigDecimal("100"), null, null);

        Bom bom = manufacturingService.createBom(
                main.getId(),
                "Mix formula",
                List.of(new ManufacturingService.BomLineInput(component.getId(), new BigDecimal("10"))));
        manufacturingService.addBomOutput(bom.getId(), main.getId(), "MAIN", new BigDecimal("70"), BigDecimal.ONE);
        manufacturingService.addBomOutput(bom.getId(), co.getId(), "CO_PRODUCT", new BigDecimal("20"), BigDecimal.ONE);
        manufacturingService.addBomOutput(bom.getId(), by.getId(), "BY_PRODUCT", new BigDecimal("10"), new BigDecimal("0.5"));

        ProductionOrder order = manufacturingService.createProductionOrder(main.getId(), BigDecimal.ONE);
        manufacturingService.allocateComponents(order.getId());
        manufacturingService.executeAssembly(order.getId(), BigDecimal.ONE);

        List<InventoryLedger> ins = ledgerRepository.findAll().stream()
                .filter(m -> "ASSEMBLY_IN".equals(m.getMovementType()))
                .toList();
        assertThat(ins).hasSize(3);
        assertThat(ins.stream().map(InventoryLedger::getReasonCode))
                .containsExactlyInAnyOrder("MAIN", "CO_PRODUCT", "BY_PRODUCT");
        assertThat(ins.stream().map(InventoryLedger::getVariantId))
                .containsExactlyInAnyOrder(main.getId(), co.getId(), by.getId());
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
}
