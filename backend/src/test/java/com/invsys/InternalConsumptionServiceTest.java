package com.invsys;

import com.invsys.api.dto.InternalRequisitionResponse;
import com.invsys.domain.InventoryLedger;
import com.invsys.domain.Location;
import com.invsys.domain.Product;
import com.invsys.domain.ProductVariant;
import com.invsys.repository.InventoryLedgerRepository;
import com.invsys.repository.LocationRepository;
import com.invsys.repository.ProductRepository;
import com.invsys.repository.ProductVariantRepository;
import com.invsys.service.InternalConsumptionService;
import com.invsys.service.InventoryService;
import com.invsys.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalConsumptionServiceTest extends AbstractIntegrationTest {

    @Autowired TestDataHelper testDataHelper;
    @Autowired InternalConsumptionService consumptionService;
    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired InventoryLedgerRepository ledgerRepository;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void createApproveAndIssueRequisitionConsumesStock() {
        UUID tenantId = testDataHelper.createTenant("Stockroom", "stock-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("STK");
        product.setName("Stockroom Item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("STK-1");
        variant = variantRepository.save(variant);

        Location warehouse = new Location();
        warehouse.setTenantId(tenantId);
        warehouse.setType("WAREHOUSE");
        warehouse.setCode("WH-STK");
        warehouse.setName("Stockroom WH");
        warehouse.setPath("/WH-STK");
        warehouse = locationRepository.save(warehouse);

        inventoryService.receive(variant.getId(), warehouse.getId(), null, new BigDecimal("10"), null, null);

        var costCenter = consumptionService.createCostCenter("KITCHEN", "Kitchen", new BigDecimal("1000"));
        assertThat(consumptionService.listCostCenters()).hasSize(1);

        InternalRequisitionResponse created = consumptionService.createRequisition(
                costCenter.getId(),
                List.of(new InternalConsumptionService.RequisitionLineInput(variant.getId(), new BigDecimal("3"))));
        assertThat(created.status()).isEqualTo("DRAFT");

        InternalRequisitionResponse approved = consumptionService.approveRequisition(created.id());
        assertThat(approved.status()).isEqualTo("APPROVED");

        InternalRequisitionResponse issued = consumptionService.issueRequisition(approved.id(), warehouse.getId());
        assertThat(issued.status()).isEqualTo("ISSUED");
        assertThat(issued.lines().getFirst().qtyIssued()).isEqualByComparingTo("3");

        List<InventoryLedger> ledger = ledgerRepository.findByTenantIdAndVariantIdOrderByCreatedAtDesc(
                tenantId, variant.getId());
        assertThat(ledger.stream().anyMatch(l ->
                "ADJUST".equals(l.getMovementType())
                        && "INTERNAL_CONSUMPTION".equals(l.getReasonCode())
                        && "INTERNAL_REQUISITION_LINE".equals(l.getReferenceType()))).isTrue();
    }

    @Test
    void cannotIssueDraftRequisition() {
        UUID tenantId = testDataHelper.createTenant("Stockroom2", "stock2-" + UUID.randomUUID().toString().substring(0, 8));
        TenantContext.setTenantId(tenantId);

        Product product = new Product();
        product.setTenantId(tenantId);
        product.setSkuRoot("STK2");
        product.setName("Item");
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setTenantId(tenantId);
        variant.setProductId(product.getId());
        variant.setSku("STK2-1");
        variant = variantRepository.save(variant);

        Location warehouse = new Location();
        warehouse.setTenantId(tenantId);
        warehouse.setType("WAREHOUSE");
        warehouse.setCode("WH-STK2");
        warehouse.setName("WH");
        warehouse.setPath("/WH-STK2");
        warehouse = locationRepository.save(warehouse);

        var cc = consumptionService.createCostCenter("OPS", "Ops", null);
        var req = consumptionService.createRequisition(cc.getId(),
                List.of(new InternalConsumptionService.RequisitionLineInput(variant.getId(), BigDecimal.ONE)));

        Location finalWarehouse = warehouse;
        assertThatThrownBy(() -> consumptionService.issueRequisition(req.id(), finalWarehouse.getId()))
                .hasMessageContaining("APPROVED");
    }
}
