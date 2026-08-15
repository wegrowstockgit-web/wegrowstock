package com.invsys.service;

import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pushes subscription entitlement changes onto the tenant dashboard SSE stream so
 * clients (SessionHydrationGate / AppShell) can refresh {@code /me} and drop locked modules.
 */
@Component
public class TenantSubscriptionUpdatedListener {

    private final DashboardSseHub dashboardSseHub;

    public TenantSubscriptionUpdatedListener(DashboardSseHub dashboardSseHub) {
        this.dashboardSseHub = dashboardSseHub;
    }

    @EventListener
    public void onUpdated(TenantSubscriptionUpdatedEvent event) {
        if (event == null || event.tenantId() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", event.tenantId().toString());
        payload.put("enabledModules", event.enabledModules().stream().map(Enum::name).toList());
        dashboardSseHub.broadcast(event.tenantId(), "TENANT_SUBSCRIPTION_UPDATED", payload);
    }
}
