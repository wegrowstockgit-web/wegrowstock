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
            result.add(new TenantTelemetryView(
                    t.tenantId(),
                    t.slug(),
                    t.status(),
                    12.5, // placeholder p50 latency ms
                    45.0, // placeholder p95 latency ms
                    multiplier));
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

        BootstrapJdbc.TenantNameSlugStatusRow tenant = bootstrapJdbc.findTenantNameSlugStatus(tenantId).orElseThrow();
        return new TenantTelemetryView(
                tenant.tenantId(),
                tenant.slug(),
                tenant.status(),
                12.5,
                45.0,
                capacityMultiplier);
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
            double capacityMultiplier
    ) {
    }
}
