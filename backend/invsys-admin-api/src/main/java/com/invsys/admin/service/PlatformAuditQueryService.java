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

    private final JdbcTemplate jdbc;

    public PlatformAuditQueryService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
    }

    public List<AuditLogRow> listRecent(int limit) {
        int capped = Math.max(1, Math.min(limit, 500));
        return jdbc.query(
                """
                SELECT id, admin_id, admin_email, action, target_tenant_id, diff_json::text, ip_address, created_at
                FROM platform_audit_logs
                ORDER BY created_at DESC
                LIMIT ?
                """,
                (rs, rowNum) -> new AuditLogRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("admin_id")),
                        rs.getString("admin_email"),
                        rs.getString("action"),
                        rs.getString("target_tenant_id") != null
                                ? UUID.fromString(rs.getString("target_tenant_id")) : null,
                        rs.getString("diff_json"),
                        rs.getString("ip_address"),
                        rs.getTimestamp("created_at").toInstant()),
                capped);
    }

    public record AuditLogRow(
            UUID id,
            UUID adminId,
            String adminEmail,
            String action,
            UUID targetTenantId,
            String diffJson,
            String ipAddress,
            Instant createdAt
    ) {
    }
}
