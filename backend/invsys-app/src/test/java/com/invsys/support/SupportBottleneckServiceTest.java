package com.invsys.support;

import com.invsys.domain.PickingBatch;
import com.invsys.repository.PickingBatchRepository;
import com.invsys.modules.sales.repository.SalesOrderRepository;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportBottleneckServiceTest {

    @Mock SalesOrderRepository salesOrderRepository;
    @Mock PickingBatchRepository pickingBatchRepository;

    SupportBottleneckService service;
    UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new SupportBottleneckService(salesOrderRepository, pickingBatchRepository);
        tenantId = UUID.randomUUID();
        TenantContext.clear();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void salesOrdersRouteSurfacesCreditHoldBeforeBackorder() {
        when(salesOrderRepository.countByTenantIdAndStatusIn(eq(tenantId), eq(List.of("BACKORDERED"))))
                .thenReturn(5L);
        when(salesOrderRepository.countByTenantIdAndStatusIn(eq(tenantId), eq(List.of("HOLD", "CREDIT_HOLD"))))
                .thenReturn(3L);

        String insight = service.detectProactiveInsight("/sales-orders");

        assertThat(insight).startsWith("💡");
        assertThat(insight).contains("3 orders are currently stuck on Credit Hold");
        assertThat(insight).contains("Tap to review");
    }

    @Test
    void salesOrdersRouteReportsBackorderedWhenNoHolds() {
        when(salesOrderRepository.countByTenantIdAndStatusIn(eq(tenantId), eq(List.of("BACKORDERED"))))
                .thenReturn(2L);
        when(salesOrderRepository.countByTenantIdAndStatusIn(eq(tenantId), eq(List.of("HOLD", "CREDIT_HOLD"))))
                .thenReturn(0L);

        String insight = service.detectProactiveInsight("/sales-orders?status=OPEN");

        assertThat(insight).contains("2 sales orders are BACKORDERED");
    }

    @Test
    void fulfillmentRouteReportsUnassignedReleasedWaves() {
        PickingBatch unassigned = new PickingBatch();
        unassigned.setAssignedUserId(null);
        PickingBatch assigned = new PickingBatch();
        assigned.setAssignedUserId(UUID.randomUUID());

        when(pickingBatchRepository.findByTenantIdAndStatus(tenantId, "RELEASED"))
                .thenReturn(List.of(unassigned, assigned));
        when(pickingBatchRepository.findByTenantIdAndStatus(tenantId, "READY")).thenReturn(List.of());
        when(pickingBatchRepository.findByTenantIdAndStatus(tenantId, "OPEN")).thenReturn(List.of());
        when(pickingBatchRepository.findByTenantIdAndStatus(tenantId, "AVAILABLE")).thenReturn(List.of());

        String insight = service.detectProactiveInsight("/fulfillment");

        assertThat(insight).contains("1 high-priority picking wave is released but unassigned");
    }

    @Test
    void returnsNullWithoutTenant() {
        TenantContext.clear();
        assertThat(service.detectProactiveInsight("/sales-orders")).isNull();
    }

    @Test
    void returnsNullWhenNoBottleneck() {
        when(salesOrderRepository.countByTenantIdAndStatusIn(eq(tenantId), eq(List.of("BACKORDERED"))))
                .thenReturn(0L);
        when(salesOrderRepository.countByTenantIdAndStatusIn(eq(tenantId), eq(List.of("HOLD", "CREDIT_HOLD"))))
                .thenReturn(0L);

        assertThat(service.detectProactiveInsight("/sales-orders")).isNull();
    }

    @Test
    void dashboardRouteSurfacesAggregateHoldAttention() {
        when(salesOrderRepository.countByTenantIdAndStatusIn(
                eq(tenantId), eq(List.of("HOLD", "CREDIT_HOLD", "BACKORDERED"))))
                .thenReturn(4L);

        String insight = service.detectProactiveInsight("/dashboard");

        assertThat(insight).startsWith("💡");
        assertThat(insight).contains("4 outbound orders need attention");
    }
}
