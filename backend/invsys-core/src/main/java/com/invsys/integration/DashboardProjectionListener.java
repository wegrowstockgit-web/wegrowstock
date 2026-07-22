package com.invsys.integration;

import com.invsys.service.DashboardKpiService;
import com.invsys.service.DashboardSseHub;
import com.invsys.core.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.invsys.core.integration.OutboxDispatchedEvent;

/**
 * Async CQRS projector + SSE broadcaster driven by outbox dispatch completion.
 */
@Component
public class DashboardProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(DashboardProjectionListener.class);

    static final Set<String> KPI_EVENTS = Set.of(
            "STOCK_LEVEL_CHANGED",
            "LEDGER_ENTRY_ARRIVED",
            "INVOICE_PAID",
            "INVOICE_OPEN",
            "SALES_ORDER_CONFIRMED",
            "ORDER_ALLOCATED",
            "SALES_ORDER_SHIPPED"
    );

    private final DashboardKpiService dashboardKpiService;
    private final DashboardSseHub sseHub;

    public DashboardProjectionListener(DashboardKpiService dashboardKpiService, DashboardSseHub sseHub) {
        this.dashboardKpiService = dashboardKpiService;
        this.sseHub = sseHub;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOutboxDispatched(OutboxDispatchedEvent event) {
        if (event == null || event.eventType() == null) {
            return;
        }
        UUID tenantId = event.tenantId();
        String eventType = event.eventType();
        Map<String, Object> payload = event.payload() != null ? event.payload() : Map.of();

        // Always push reactive stream updates for known warehouse / finance events.
        if (KPI_EVENTS.contains(eventType)
                || eventType.contains("INVOICE")
                || eventType.contains("ORDER")
                || eventType.contains("STOCK")
                || eventType.contains("LEDGER")
                || eventType.contains("CYCLE")) {
            sseHub.broadcast(tenantId, eventType, payload);
        }

        if (!KPI_EVENTS.contains(eventType)) {
            return;
        }

        TenantContext.setTenantId(tenantId);
        try {
            dashboardKpiService.refresh(tenantId, eventType);
            sseHub.broadcast(tenantId, "DASHBOARD_KPI_REFRESHED", Map.of(
                    "sourceEventType", eventType,
                    "aggregateId", event.aggregateId() != null ? event.aggregateId().toString() : ""));
        } catch (Exception ex) {
            log.warn("Dashboard KPI projection failed tenant={} type={}: {}",
                    tenantId, eventType, ex.toString());
        } finally {
            TenantContext.clear();
        }
    }
}
