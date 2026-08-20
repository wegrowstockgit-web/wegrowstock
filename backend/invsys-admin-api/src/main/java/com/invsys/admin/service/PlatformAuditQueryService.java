package com.invsys.admin.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PlatformAuditQueryService {

    public static final String ACTOR_IMPERSONATION = "PLATFORM_ADMIN_IMPERSONATION";

    private final JdbcTemplate jdbc;

    public PlatformAuditQueryService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
    }

    public List<AuditLogRow> listRecent(int limit) {
        return listRecent(limit, false);
    }

    public List<AuditLogRow> listRecent(int limit, boolean impersonationOnly) {
        int capped = Math.max(1, Math.min(limit, 500));
        String sql = """
                SELECT id, admin_id, admin_email, action, target_tenant_id, diff_json::text, ip_address,
                       created_at, actor_type
                FROM platform_audit_logs
                """;
        if (impersonationOnly) {
            sql += """
                    WHERE actor_type = ? OR action = 'TENANT_IMPERSONATE'
                    """;
        }
        sql += """
                ORDER BY created_at DESC
                LIMIT ?
                """;
        if (impersonationOnly) {
            return jdbc.query(sql, this::mapRow, ACTOR_IMPERSONATION, capped);
        }
        return jdbc.query(sql, this::mapRow, capped);
    }

    private AuditLogRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AuditLogRow(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("admin_id")),
                rs.getString("admin_email"),
                rs.getString("action"),
                rs.getString("target_tenant_id") != null
                        ? UUID.fromString(rs.getString("target_tenant_id")) : null,
                rs.getString("diff_json"),
                rs.getString("ip_address"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getString("actor_type"));
    }

    public record AuditLogRow(
            UUID id,
            UUID adminId,
            String adminEmail,
            String action,
            UUID targetTenantId,
            String diffJson,
            String ipAddress,
            Instant createdAt,
            String actorType
    ) {
    }
}
