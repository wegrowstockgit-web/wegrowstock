package com.invsys.service;

import com.invsys.domain.DemandForecast;
import com.invsys.modules.catalog.domain.ProductVariant;
import com.invsys.repository.DemandForecastRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import com.invsys.modules.catalog.repository.ProductVariantRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderLineRepository;
import com.invsys.modules.purchasing.repository.PurchaseOrderRepository;
import com.invsys.modules.sales.repository.SalesOrderLineRepository;
import com.invsys.repository.TenantRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastingLifecycleExclusionTest {

    private static final UUID TENANT = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID VARIANT_ID = UUID.fromString("c0000000-0000-4000-8000-000000000801");

    @Mock ProductVariantRepository variantRepository;
    @Mock InventoryLevelRepository levelRepository;
    @Mock SalesOrderLineRepository salesOrderLineRepository;
    @Mock PurchaseOrderLineRepository purchaseOrderLineRepository;
    @Mock PurchaseOrderRepository purchaseOrderRepository;
    @Mock DemandForecastRepository forecastRepository;
    @Mock TenantRepository tenantRepository;
    @Mock ForecastingInferenceService inferenceService;

    private ForecastingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ForecastingWorker(
                variantRepository,
                levelRepository,
                salesOrderLineRepository,
                purchaseOrderLineRepository,
                purchaseOrderRepository,
                forecastRepository,
                tenantRepository,
                inferenceService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void discontinuedVariantsGetZeroRecommendedPoWithoutInference() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setTenantId(TENANT);
        variant.setSku("DISC-1");
        variant.setLifecycleStatus("DISCONTINUED");

        when(salesOrderLineRepository.sumQtyOrderedByVariantSince(any(), any())).thenReturn(List.of());
        when(levelRepository.findAll()).thenReturn(List.of());
        when(purchaseOrderRepository.findAll()).thenReturn(List.of());
        when(purchaseOrderLineRepository.findAll()).thenReturn(List.of());
        when(variantRepository.findAll()).thenReturn(List.of(variant));
        when(forecastRepository.findByTenantIdAndVariantId(TENANT, VARIANT_ID)).thenReturn(Optional.empty());
        when(forecastRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.calculateForTenant(TENANT);

        verify(inferenceService, never()).infer(any(), any(), any(), any());
        ArgumentCaptor<DemandForecast> captor = ArgumentCaptor.forClass(DemandForecast.class);
        verify(forecastRepository).save(captor.capture());
        DemandForecast saved = captor.getValue();
        assertThat(saved.getRecommendedPoQty()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getExternalSignals()).containsEntry("excludedFromReplenishment", true);
    }
}
