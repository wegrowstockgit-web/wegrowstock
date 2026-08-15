package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminDeadLetterService {

    private final JdbcTemplate jdbc;

    public AdminDeadLetterService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
    }

    public List<DeadLetterGroup> listGrouped() {
        return jdbc.query(
                """
                SELECT tenant_id, COUNT(*) AS dead_count,
                       MAX(created_at) AS latest_at
                FROM outbox_events
                WHERE status = 'FAILED'
                GROUP BY tenant_id
                ORDER BY dead_count DESC
                """,
                (rs, rowNum) -> new DeadLetterGroup(
                        UUID.fromString(rs.getString("tenant_id")),
                        rs.getLong("dead_count"),
                        rs.getTimestamp("latest_at") != null
                                ? rs.getTimestamp("latest_at").toInstant() : null));
    }

    public DeadLetterDetail get(UUID id) {
        List<DeadLetterDetail> rows = jdbc.query(
                """
                SELECT id, tenant_id, aggregate_type, aggregate_id, event_type, status,
                       retry_count, last_error, created_at, next_attempt_at
                FROM outbox_events
                WHERE id = ?
                """,
                (rs, rowNum) -> new DeadLetterDetail(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("tenant_id")),
                        rs.getString("aggregate_type"),
                        UUID.fromString(rs.getString("aggregate_id")),
                        rs.getString("event_type"),
                        rs.getString("status"),
                        rs.getInt("retry_count"),
                        rs.getString("last_error"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("next_attempt_at") != null
                                ? rs.getTimestamp("next_attempt_at").toInstant() : null),
                id);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Dead-letter event not found");
        }
        return rows.get(0);
    }

    @Transactional
    public DeadLetterDetail retry(UUID id) {
        int updated = jdbc.update("""
                UPDATE outbox_events
                SET status = 'PENDING',
                    retry_count = 0,
                    next_attempt_at = NOW(),
                    last_error = NULL,
                    updated_at = NOW()
                WHERE id = ?
                """, id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Dead-letter event not found");
        }
        return get(id);
    }

    public record DeadLetterGroup(UUID tenantId, long count, Instant latestAt) {
    }

    public record DeadLetterDetail(
            UUID id,
            UUID tenantId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String status,
            int retryCount,
            String lastError,
            Instant createdAt,
            Instant nextAttemptAt
    ) {
    }
}
