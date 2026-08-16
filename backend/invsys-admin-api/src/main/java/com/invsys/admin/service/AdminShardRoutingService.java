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
public class AdminShardRoutingService {

    private final JdbcTemplate jdbc;
    private final BootstrapJdbc bootstrapJdbc;

    public AdminShardRoutingService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                                    BootstrapJdbc bootstrapJdbc) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.bootstrapJdbc = bootstrapJdbc;
    }

    public List<ShardRouteView> listAll() {
        return jdbc.query(
                """
                SELECT tenant_id, shard_key, jdbc_url, aurora_cluster, region, notes, updated_at
                FROM tenant_shard_routing
                ORDER BY shard_key, tenant_id
                """,
                (rs, rowNum) -> mapRow(rs));
    }

    public ShardRouteView get(UUID tenantId) {
        List<ShardRouteView> rows = jdbc.query(
                """
                SELECT tenant_id, shard_key, jdbc_url, aurora_cluster, region, notes, updated_at
                FROM tenant_shard_routing
                WHERE tenant_id = ?
                """,
                (rs, rowNum) -> mapRow(rs),
                tenantId);
        if (rows.isEmpty()) {
            return new ShardRouteView(tenantId, "primary", null, null, "us-east-1", null, null);
        }
        return rows.get(0);
    }

    @Transactional
    public ShardRouteView upsert(UUID tenantId, ShardUpsertRequest request) {
        bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        String shardKey = request.shardKey() == null || request.shardKey().isBlank() ? "primary" : request.shardKey();
        String region = request.region() == null || request.region().isBlank() ? "us-east-1" : request.region();
        UUID updatedBy = currentAdminId();

        jdbc.update("""
                INSERT INTO tenant_shard_routing (
                    tenant_id, shard_key, jdbc_url, aurora_cluster, region, notes, updated_at, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, NOW(), ?)
                ON CONFLICT (tenant_id) DO UPDATE SET
                    shard_key = EXCLUDED.shard_key,
                    jdbc_url = EXCLUDED.jdbc_url,
                    aurora_cluster = EXCLUDED.aurora_cluster,
                    region = EXCLUDED.region,
                    notes = EXCLUDED.notes,
                    updated_at = NOW(),
                    updated_by = EXCLUDED.updated_by
                """,
                tenantId,
                shardKey,
                request.jdbcUrl(),
                request.auroraCluster(),
                region,
                request.notes(),
                updatedBy);

        return get(tenantId);
    }

    private static ShardRouteView mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ShardRouteView(
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("shard_key"),
                rs.getString("jdbc_url"),
                rs.getString("aurora_cluster"),
                rs.getString("region"),
                rs.getString("notes"),
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toInstant() : null);
    }

    private static UUID currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    public record ShardUpsertRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) String shardKey,
            @jakarta.validation.constraints.Size(max = 512) String jdbcUrl,
            @jakarta.validation.constraints.Size(max = 128) String auroraCluster,
            @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9-]{0,32}$") String region,
            @jakarta.validation.constraints.Size(max = 500) String notes
    ) {
    }

    public record ShardRouteView(
            UUID tenantId,
            String shardKey,
            String jdbcUrl,
            String auroraCluster,
            String region,
            String notes,
            Instant updatedAt
    ) {
    }
}
