package com.invsys.service;

import com.invsys.domain.AccountMapping;
import com.invsys.domain.IntegrationSyncLog;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.domain.TenantSettings;
import com.invsys.repository.AccountMappingRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.repository.TenantSettingsRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock InventoryLevelRepository levelRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock AccountMappingRepository accountMappingRepository;
    @Mock IntegrationSyncLogRepository syncLogRepository;
    @Mock TenantSettingsRepository tenantSettingsRepository;

    ReconciliationService service;
    UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(
                levelRepository,
                variantRepository,
                accountMappingRepository,
                syncLogRepository,
                tenantSettingsRepository);
        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reportSurvivesFailedSyncLogsWithNullEntityId() {
        UUID variantId = UUID.randomUUID();
        InventoryLevel level = new InventoryLevel();
        level.setVariantId(variantId);
        level.setOnHand(new BigDecimal("10"));

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setAvgCost(new BigDecimal("2.50"));

        IntegrationSyncLog failed = new IntegrationSyncLog();
        failed.setSystem("AMAZON");
        failed.setEntityType("ORDER");
        failed.setEntityId(null);
        failed.setExternalId("amz-order-9");
        failed.setStatus("FAILED");
        failed.setLastError("No adapter registered for channel: AMAZON");

        TenantSettings settings = new TenantSettings();
        settings.setSettings(Map.of("currency", "USD"));

        when(levelRepository.findAll()).thenReturn(List.of(level));
        when(variantRepository.findAll()).thenReturn(List.of(variant));
        when(accountMappingRepository.findByTenantIdOrderBySystemAscAccountTypeAsc(tenantId)).thenReturn(List.of());
        when(syncLogRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "FAILED"))
                .thenReturn(List.of(failed));
        when(tenantSettingsRepository.findAll()).thenReturn(List.of(settings));

        ReconciliationService.ReconciliationReport report = service.report();

        assertThat(report.physicalInventoryValue()).isEqualByComparingTo("25.00");
        assertThat(report.accountingInventoryValue()).isEqualByComparingTo("25.00");
        assertThat(report.driftAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(report.currency()).isEqualTo("USD");
        assertThat(report.syncDrifts()).hasSize(1);
        assertThat(report.syncDrifts().getFirst().entityId()).isEqualTo("amz-order-9");
        assertThat(report.syncDrifts().getFirst().message()).contains("AMAZON");
    }

    @Test
    void resolveEntityKeyFallsBackToEmDashWhenNoIds() {
        IntegrationSyncLog log = new IntegrationSyncLog();
        assertThat(ReconciliationService.resolveEntityKey(log)).isEqualTo("n/a");
    }

    @Test
    void reportAppliesAccountingHaircutWhenInventoryMappingsExist() {
        UUID variantId = UUID.randomUUID();
        InventoryLevel level = new InventoryLevel();
        level.setVariantId(variantId);
        level.setOnHand(new BigDecimal("100"));

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setAvgCost(BigDecimal.ONE);

        AccountMapping mapping = new AccountMapping();
        mapping.setAccountType("INVENTORY");

        when(levelRepository.findAll()).thenReturn(List.of(level));
        when(variantRepository.findAll()).thenReturn(List.of(variant));
        when(accountMappingRepository.findByTenantIdOrderBySystemAscAccountTypeAsc(tenantId))
                .thenReturn(List.of(mapping));
        when(syncLogRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "FAILED"))
                .thenReturn(List.of());
        when(tenantSettingsRepository.findAll()).thenReturn(List.of());

        ReconciliationService.ReconciliationReport report = service.report();

        assertThat(report.physicalInventoryValue()).isEqualByComparingTo("100");
        assertThat(report.accountingInventoryValue()).isEqualByComparingTo("98.00");
        assertThat(report.driftAmount()).isEqualByComparingTo("2.00");
        assertThat(report.mappedAccounts()).isEqualTo(1);
        assertThat(report.currency()).isEqualTo("USD");
    }
}
