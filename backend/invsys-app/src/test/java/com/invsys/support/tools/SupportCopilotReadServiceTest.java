package com.invsys.support.tools;

import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.modules.sales.domain.SalesOrder;
import com.invsys.modules.sales.domain.SalesOrderLine;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryRequest;
import com.invsys.support.tools.SupportCopilotToolModels.LedgerHistoryResponse;
import com.invsys.modules.sales.repository.CustomerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.modules.inventory.service.InventoryService;
import com.invsys.support.tools.SupportCopilotToolModels.AtpRequest;
import com.invsys.support.tools.SupportCopilotToolModels.AtpResponse;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusRequest;
import com.invsys.support.tools.SupportCopilotToolModels.OrderStatusResponse;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportCopilotReadServiceTest {

    @Mock ProductVariantRepository variantRepository;
    @Mock InventoryLevelRepository levelRepository;
    @Mock LocationRepository locationRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Mock SalesOrderRepository salesOrderRepository;
    @Mock SalesOrderLineRepository salesOrderLineRepository;
    @Mock CustomerRepository customerRepository;
    @Mock InventoryService inventoryService;

    SupportCopilotReadService service;
    UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
        service = new SupportCopilotReadService(
                variantRepository,
                levelRepository,
                locationRepository,
                purchaseOrderRepository,
                purchaseOrderLineRepository,
                salesOrderRepository,
                salesOrderLineRepository,
                customerRepository,
                inventoryService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void checkAvailableToPromiseUsesTenantContextNeverTrustsCallerTenant() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setTenantId(tenantId);
        variant.setSku("SKU-ATP-1");
        when(variantRepository.findByTenantIdAndSku(eq(tenantId), eq("SKU-ATP-1")))
                .thenReturn(Optional.of(variant));

        InventoryLevel level = new InventoryLevel();
        level.setTenantId(tenantId);
        level.setVariantId(variantId);
        level.setLocationId(UUID.randomUUID());
        level.setOnHand(new BigDecimal("12"));
        level.setAllocated(new BigDecimal("5"));
        when(levelRepository.findByTenantIdAndVariantId(eq(tenantId), eq(variantId)))
                .thenReturn(List.of(level));
        when(purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId)))
                .thenReturn(List.of());

        AtpResponse response = service.checkAvailableToPromise(new AtpRequest("SKU-ATP-1", null));

        assertThat(response.sku()).isEqualTo("SKU-ATP-1");
        assertThat(response.onHand()).isEqualByComparingTo("12");
        assertThat(response.allocated()).isEqualByComparingTo("5");
        assertThat(response.availableToPromise()).isEqualByComparingTo("7");
    }

    @Test
    void checkAvailableToPromiseRequiresTenantContext() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.checkAvailableToPromise(new AtpRequest("SKU-1", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant");
    }

    @Test
    void checkOrderStatusReportsBackorderHoldAndMissingSku() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        SalesOrder order = new SalesOrder();
        order.setId(orderId);
        order.setTenantId(tenantId);
        order.setNumber("SO-9001");
        order.setStatus("BACKORDERED");
        order.setCustomerId(UUID.randomUUID());
        when(salesOrderRepository.findByTenantIdAndNumberIgnoreCase(eq(tenantId), eq("SO-9001")))
                .thenReturn(Optional.of(order));

        SalesOrderLine line = new SalesOrderLine();
        line.setSalesOrderId(orderId);
        line.setVariantId(variantId);
        line.setQtyOrdered(new BigDecimal("10"));
        line.setQtyAllocated(new BigDecimal("2"));
        when(salesOrderLineRepository.findBySalesOrderId(eq(orderId))).thenReturn(List.of(line));

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setTenantId(tenantId);
        variant.setSku("WIDGET-A");
        when(variantRepository.findById(eq(variantId))).thenReturn(Optional.of(variant));

        OrderStatusResponse response = service.checkOrderStatus(new OrderStatusRequest("SO-9001"));

        assertThat(response.status()).isEqualTo("BACKORDERED");
        assertThat(response.holdReason()).containsIgnoringCase("stock");
        assertThat(response.missingSku()).isEqualTo("WIDGET-A");
    }

    @Test
    void extractorsFindSkuAndOrderNumber() {
        assertThat(SupportCopilotReadService.extractOrderNumber("Why is SO-1001 stuck?"))
                .isEqualTo("SO-1001");
        assertThat(SupportCopilotReadService.extractOrderNumber("status of SO-2026-00030?"))
                .isEqualTo("SO-2026-00030");
        assertThat(SupportCopilotReadService.extractSku("What is ATP for SKU WIDGET-A?"))
                .isEqualTo("WIDGET-A");
    }

    @Test
    void getLedgerHistorySummaryReturnsRecentMovements() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setTenantId(tenantId);
        variant.setSku("SKU-LEDGER");
        when(variantRepository.findByTenantIdAndSku(eq(tenantId), eq("SKU-LEDGER")))
                .thenReturn(Optional.of(variant));

        InventoryLedger row = new InventoryLedger();
        row.setTenantId(tenantId);
        row.setVariantId(variantId);
        row.setLocationId(UUID.randomUUID());
        row.setMovementType("RECEIVE");
        row.setQuantityDelta(new BigDecimal("3"));
        row.setReasonCode("PO_RECEIVE");
        when(inventoryService.listRecentLedger(eq(5), eq(variantId))).thenReturn(List.of(row));

        LedgerHistoryResponse response =
                service.getLedgerHistorySummary(new LedgerHistoryRequest("SKU-LEDGER", 5));

        assertThat(response.sku()).isEqualTo("SKU-LEDGER");
        assertThat(response.movements()).hasSize(1);
        assertThat(response.movements().getFirst().movementType()).isEqualTo("RECEIVE");
        assertThat(response.movements().getFirst().quantityDelta()).isEqualTo("3");
    }

    @Test
    void formatLiveFactsForPromptCombinesOrderAndSku() {
        UUID orderId = UUID.randomUUID();
        SalesOrder order = new SalesOrder();
        order.setId(orderId);
        order.setTenantId(tenantId);
        order.setNumber("SO-55");
        order.setStatus("ALLOCATED");
        order.setCustomerId(UUID.randomUUID());
        when(salesOrderRepository.findByTenantIdAndNumberIgnoreCase(eq(tenantId), eq("SO-55")))
                .thenReturn(Optional.of(order));

        UUID variantId = UUID.randomUUID();
        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setTenantId(tenantId);
        variant.setSku("SKU-X");
        when(variantRepository.findByTenantIdAndSku(eq(tenantId), eq("SKU-X")))
                .thenReturn(Optional.of(variant));
        when(levelRepository.findByTenantIdAndVariantId(eq(tenantId), eq(variantId)))
                .thenReturn(List.of());
        when(purchaseOrderRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId)))
                .thenReturn(List.of());

        String facts = service.formatLiveFactsForPrompt(
                "Why is SO-55 stuck and what is ATP for SKU SKU-X?", null);

        assertThat(facts).contains("SO-55");
        assertThat(facts).contains("ALLOCATED");
        assertThat(facts).contains("SKU-X");
        assertThat(facts).contains("available-to-promise");
    }
}
