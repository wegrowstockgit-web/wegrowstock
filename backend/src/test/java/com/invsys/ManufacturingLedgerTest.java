package com.invsys;

import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.domain.ProductionOrder;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.service.InventoryService;
import com.invsys.service.ManufacturingService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManufacturingLedgerTest extends AbstractIntegrationTest {

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
    void assemblyWritesAppendOnlyLedgerMovements() {
        UUID tenantId = testDataHelper.createTenant("Mfg Tenant", "mfg-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product parentProduct = new Product();
        parentProduct.setTenantId(tenantId);
        parentProduct.setSkuRoot("ASM");
        parentProduct.setName("Assembly");
        parentProduct = productRepository.save(parentProduct);

        Product componentProduct = new Product();
        componentProduct.setTenantId(tenantId);
        componentProduct.setSkuRoot("CMP");
        componentProduct.setName("Component");
        componentProduct = productRepository.save(componentProduct);

        ProductVariant parentVariant = new ProductVariant();
        parentVariant.setTenantId(tenantId);
        parentVariant.setProductId(parentProduct.getId());
        parentVariant.setSku("ASM-1");
        parentVariant = variantRepository.save(parentVariant);

        ProductVariant componentVariant = new ProductVariant();
        componentVariant.setTenantId(tenantId);
        componentVariant.setProductId(componentProduct.getId());
        componentVariant.setSku("CMP-1");
        componentVariant = variantRepository.save(componentVariant);

        Location location = new Location();
        location.setTenantId(tenantId);
        location.setType("WAREHOUSE");
        location.setCode("WH-MFG");
        location.setName("Mfg WH");
        location.setPath("/WH-MFG");
        location = locationRepository.save(location);

        inventoryService.receive(componentVariant.getId(), location.getId(), null, new BigDecimal("20"), null, null);

        manufacturingService.createBom(
                parentVariant.getId(),
                "Kit BOM",
                List.of(new ManufacturingService.BomLineInput(componentVariant.getId(), new BigDecimal("2"))));

        ProductionOrder order = manufacturingService.createProductionOrder(parentVariant.getId(), new BigDecimal("3"));
        manufacturingService.allocateComponents(order.getId());
        manufacturingService.executeAssembly(order.getId(), new BigDecimal("3"));

        List<InventoryLedger> movements = ledgerRepository.findAll();
        assertThat(movements.stream().map(InventoryLedger::getMovementType))
                .contains("ASSEMBLY_IN", "ASSEMBLY_OUT", "RECEIVE");
        assertThat(movements.stream().filter(m -> "ASSEMBLY_IN".equals(m.getMovementType()))
                .map(InventoryLedger::getQuantityDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(new BigDecimal("3"));
    }
}
