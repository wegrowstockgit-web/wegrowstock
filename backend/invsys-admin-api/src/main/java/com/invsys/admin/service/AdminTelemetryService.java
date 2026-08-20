package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.ratelimit.DistributedRateLimiter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminTelemetryService {

    private final JdbcTemplate jdbc;
    private final BootstrapJdbc bootstrapJdbc;
    private final DistributedRateLimiter distributedRateLimiter;

    public AdminTelemetryService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                                 BootstrapJdbc bootstrapJdbc,
                                 DistributedRateLimiter distributedRateLimiter) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.bootstrapJdbc = bootstrapJdbc;
        this.distributedRateLimiter = distributedRateLimiter;
    }

    public List<TenantTelemetryView> listTenants() {
        List<BootstrapJdbc.TenantWithSubscriptionRow> tenants = bootstrapJdbc.listTenantsWithSubscriptions();
        List<TenantTelemetryView> result = new ArrayList<>();
        for (BootstrapJdbc.TenantWithSubscriptionRow t : tenants) {
            double multiplier = bootstrapJdbc.findRateCapacityMultiplier(t.tenantId());
            BootstrapJdbc.TenantThrottleRow throttle = bootstrapJdbc.findTenantThrottle(t.tenantId());
            result.add(toView(t.tenantId(), t.slug(), t.status(), multiplier, throttle));
        }
        return result;
    }

    @Transactional
    public TenantTelemetryView setRateLimit(UUID tenantId, double capacityMultiplier) {
        if (capacityMultiplier <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MULTIPLIER",
                    "capacityMultiplier must be > 0");
        }
        bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        UUID updatedBy = currentAdminId();
        jdbc.update("""
                INSERT INTO tenant_rate_limit_overrides (tenant_id, capacity_multiplier, updated_at, updated_by)
                VALUES (?, ?, NOW(), ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    capacity_multiplier = EXCLUDED.capacity_multiplier,
                    updated_at = NOW(),
                    updated_by = EXCLUDED.updated_by
                """,
                tenantId, capacityMultiplier, updatedBy);

        distributedRateLimiter.setTenantCapacityMultiplier(tenantId, capacityMultiplier);
        return currentView(tenantId, capacityMultiplier);
    }

    @Transactional
    public TenantTelemetryView setThrottle(UUID tenantId, Integer customRateLimit, boolean throttled) {
        if (customRateLimit != null && customRateLimit <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RATE_LIMIT",
                    "customRateLimit must be > 0 when set");
        }
        bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        bootstrapJdbc.updateTenantThrottle(tenantId, customRateLimit, throttled);
        distributedRateLimiter.setTenantThrottle(tenantId, throttled, customRateLimit);
        return currentView(tenantId, bootstrapJdbc.findRateCapacityMultiplier(tenantId));
    }

    private TenantTelemetryView currentView(UUID tenantId, double multiplier) {
        BootstrapJdbc.TenantNameSlugStatusRow tenant = bootstrapJdbc.findTenantNameSlugStatus(tenantId).orElseThrow();
        BootstrapJdbc.TenantThrottleRow throttle = bootstrapJdbc.findTenantThrottle(tenantId);
        return toView(tenant.tenantId(), tenant.slug(), tenant.status(), multiplier, throttle);
    }

    private static TenantTelemetryView toView(UUID tenantId, String slug, String status,
                                              double multiplier, BootstrapJdbc.TenantThrottleRow throttle) {
        return new TenantTelemetryView(
                tenantId,
                slug,
                status,
                12.5,
                45.0,
                multiplier,
                throttle == null ? null : throttle.customRateLimit(),
                throttle != null && throttle.throttled());
    }

    private static UUID currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    public record TenantTelemetryView(
            UUID tenantId,
            String slug,
            String status,
            double p50LatencyMs,
            double p95LatencyMs,
            double capacityMultiplier,
            Integer customRateLimit,
            boolean isThrottled
    ) {
    }
}
