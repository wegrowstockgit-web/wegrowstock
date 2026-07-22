package com.invsys.service;

import com.invsys.modules.inventory.domain.CycleCount;
import com.invsys.modules.inventory.domain.CycleCountLine;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.modules.inventory.repository.CycleCountLineRepository;
import com.invsys.modules.inventory.repository.CycleCountRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.LocationRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.invsys.modules.inventory.service.CycleCountService;
import com.invsys.modules.inventory.service.InventoryService;

@ExtendWith(MockitoExtension.class)
class CycleCountServiceTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID COUNT_ID = UUID.fromString("a0000000-0000-4000-8000-000000001901");
    private static final UUID LINE_ID = UUID.fromString("a0000000-0000-4000-8000-000000001911");
    private static final UUID VARIANT_ID = UUID.fromString("a0000000-0000-4000-8000-000000000801");
    private static final UUID LOCATION_ID = UUID.fromString("a0000000-0000-4000-8000-000000000604");

    @Mock InventoryLedgerRepository ledgerRepository;
    @Mock CycleCountRepository cycleCountRepository;
    @Mock CycleCountLineRepository cycleCountLineRepository;
    @Mock LocationRepository locationRepository;
    @Mock InventoryLevelRepository inventoryLevelRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock TenantSettingsRepository tenantSettingsRepository;
    @Mock InventoryService inventoryService;

    private CycleCountService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT);
        service = new CycleCountService(
                ledgerRepository,
                cycleCountRepository,
                cycleCountLineRepository,
                locationRepository,
                inventoryLevelRepository,
                productVariantRepository,
                tenantSettingsRepository,
                inventoryService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ruleA_exactMatchAutoApprovesWithoutLedgerTouch() {
        stubSubmitContext(new BigDecimal("35"), new BigDecimal("8.00"), new BigDecimal("100.00"));

        CycleCountService.CycleCountLineView view =
                service.submitCountedQty(COUNT_ID, LINE_ID, new BigDecimal("35"));

        assertThat(view.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_AUTO_APPROVED);
        assertThat(view.financialImpact()).isEqualByComparingTo("0.0000");
        verify(inventoryService, never()).adjust(any(), any(), any(), any(), any());
        ArgumentCaptor<CycleCountLine> captor = ArgumentCaptor.forClass(CycleCountLine.class);
        verify(cycleCountLineRepository).save(captor.capture());
        assertThat(captor.getValue().getVarianceStatus()).isEqualTo(CycleCountService.VARIANCE_AUTO_APPROVED);
    }

    @Test
    void ruleB_smallFinancialImpactAutoApprovesAndAdjustsLedger() {
        // expected 35, counted 34, avgCost 8 → impact 8 < 100
        stubSubmitContext(new BigDecimal("35"), new BigDecimal("8.00"), new BigDecimal("100.00"));

        CycleCountService.CycleCountLineView view =
                service.submitCountedQty(COUNT_ID, LINE_ID, new BigDecimal("34"));

        assertThat(view.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_AUTO_APPROVED);
        assertThat(view.financialImpact()).isEqualByComparingTo("8.0000");
        verify(inventoryService).adjust(
                eq(VARIANT_ID),
                eq(LOCATION_ID),
                isNull(),
                eq(new BigDecimal("-1")),
                eq("CYCLE_COUNT"));
    }

    @Test
    void ruleC_largeFinancialImpactEscalatesWithoutLedgerTouch() {
        // expected 35, counted 0, avgCost 8 → impact 280 > 100
        stubSubmitContext(new BigDecimal("35"), new BigDecimal("8.00"), new BigDecimal("100.00"));

        CycleCountService.CycleCountLineView view =
                service.submitCountedQty(COUNT_ID, LINE_ID, BigDecimal.ZERO);

        assertThat(view.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_PENDING_MANAGER_REVIEW);
        assertThat(view.financialImpact()).isEqualByComparingTo("280.0000");
        verify(inventoryService, never()).adjust(any(), any(), any(), any(), any());
    }

    @Test
    void approveLedgerAdjustmentAppliesDeltaAndMarksApproved() {
        CycleCount count = openCount();
        CycleCountLine line = pendingLine(new BigDecimal("35"), new BigDecimal("0"), new BigDecimal("280"));
        line.setVarianceStatus(CycleCountService.VARIANCE_PENDING_MANAGER_REVIEW);
        when(cycleCountLineRepository.findByIdAndTenantId(LINE_ID, TENANT)).thenReturn(Optional.of(line));
        when(cycleCountRepository.findByIdAndTenantId(COUNT_ID, TENANT)).thenReturn(Optional.of(count));
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant(new BigDecimal("8"))));
        when(cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(COUNT_ID)).thenReturn(List.of(line));

        CycleCountService.CycleCountLineView view = service.approveLedgerAdjustment(LINE_ID);

        assertThat(view.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_APPROVED);
        verify(inventoryService).adjust(
                eq(VARIANT_ID),
                eq(LOCATION_ID),
                isNull(),
                eq(new BigDecimal("-35")),
                eq("CYCLE_COUNT"));
    }

    @Test
    void requestRecountClearsCountAndReturnsToPendingQueue() {
        CycleCount count = openCount();
        count.setNotes("Monthly bin count");
        CycleCountLine line = pendingLine(new BigDecimal("35"), new BigDecimal("0"), new BigDecimal("280"));
        line.setVarianceStatus(CycleCountService.VARIANCE_PENDING_MANAGER_REVIEW);
        when(cycleCountLineRepository.findByIdAndTenantId(LINE_ID, TENANT)).thenReturn(Optional.of(line));
        when(cycleCountRepository.findByIdAndTenantId(COUNT_ID, TENANT)).thenReturn(Optional.of(count));
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant(new BigDecimal("8"))));

        CycleCountService.CycleCountLineView view = service.requestRecount(LINE_ID);

        assertThat(view.varianceStatus()).isEqualTo(CycleCountService.VARIANCE_PENDING);
        assertThat(view.countedQty()).isNull();
        assertThat(count.getNotes()).contains("Recount requested");
        verify(inventoryService, never()).adjust(any(), any(), any(), any(), any());
    }

    private void stubSubmitContext(BigDecimal expected, BigDecimal avgCost, BigDecimal threshold) {
        CycleCount count = openCount();
        CycleCountLine line = pendingLine(expected, null, null);
        TenantSettings settings = TenantSettings.withDefaults(TENANT);
        settings.setMaxAutoAdjustValue(threshold);
        settings.setBlindCycleCounts(true);

        when(cycleCountRepository.findByIdAndTenantId(COUNT_ID, TENANT)).thenReturn(Optional.of(count));
        when(cycleCountLineRepository.findByIdAndTenantId(LINE_ID, TENANT)).thenReturn(Optional.of(line));
        when(productVariantRepository.findById(VARIANT_ID)).thenReturn(Optional.of(variant(avgCost)));
        when(tenantSettingsRepository.findByTenantId(TENANT)).thenReturn(Optional.of(settings));
        lenient().when(cycleCountLineRepository.findByCycleCountIdOrderByCreatedAtAsc(COUNT_ID))
                .thenReturn(List.of(line));
    }

    private static CycleCount openCount() {
        CycleCount count = new CycleCount();
        count.setId(COUNT_ID);
        count.setTenantId(TENANT);
        count.setLocationId(LOCATION_ID);
        count.setStatus("IN_PROGRESS");
        return count;
    }

    private static CycleCountLine pendingLine(BigDecimal expected, BigDecimal counted, BigDecimal impact) {
        CycleCountLine line = new CycleCountLine();
        line.setId(LINE_ID);
        line.setTenantId(TENANT);
        line.setCycleCountId(COUNT_ID);
        line.setVariantId(VARIANT_ID);
        line.setLotId(null);
        line.setExpectedQty(expected);
        line.setCountedQty(counted);
        line.setFinancialImpact(impact);
        line.setVarianceStatus(CycleCountService.VARIANCE_PENDING);
        return line;
    }

    private static ProductVariant variant(BigDecimal avgCost) {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setTenantId(TENANT);
        variant.setSku("WIDGET-S");
        variant.setAvgCost(avgCost);
        return variant;
    }
}
