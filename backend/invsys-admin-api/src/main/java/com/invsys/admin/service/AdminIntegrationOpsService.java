package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminIntegrationOpsService {

    private final JdbcTemplate jdbc;
    private final BootstrapJdbc bootstrapJdbc;

    public AdminIntegrationOpsService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                                      BootstrapJdbc bootstrapJdbc) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.bootstrapJdbc = bootstrapJdbc;
    }

    public List<TrafficRow> trafficLast24h() {
        return jdbc.query(
                """
                SELECT oe.tenant_id, oe.status, COUNT(*) AS event_count
                FROM outbox_events oe
                WHERE oe.created_at >= NOW() - INTERVAL '24 hours'
                GROUP BY oe.tenant_id, oe.status
                ORDER BY event_count DESC, oe.tenant_id, oe.status
                """,
                (rs, rowNum) -> new TrafficRow(
                        UUID.fromString(rs.getString("tenant_id")),
                        rs.getString("status"),
                        rs.getLong("event_count")));
    }

    @Transactional
    public KillSwitchView setKillSwitch(UUID tenantId, boolean paused, String reason) {
        bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        UUID updatedBy = currentAdminId();
        jdbc.update("""
                INSERT INTO tenant_integration_controls (tenant_id, sync_paused, paused_reason, updated_at, updated_by)
                VALUES (?, ?, ?, NOW(), ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    sync_paused = EXCLUDED.sync_paused,
                    paused_reason = EXCLUDED.paused_reason,
                    updated_at = NOW(),
                    updated_by = EXCLUDED.updated_by
                """,
                tenantId, paused, reason, updatedBy);

        return new KillSwitchView(tenantId, paused, reason, Instant.now());
    }

    private static UUID currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    public record TrafficRow(UUID tenantId, String status, long eventCount) {
    }

    public record KillSwitchView(UUID tenantId, boolean paused, String reason, Instant updatedAt) {
    }
}
