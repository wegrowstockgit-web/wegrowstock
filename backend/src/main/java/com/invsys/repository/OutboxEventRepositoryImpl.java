package com.invsys.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OutboxEventRepositoryImpl implements OutboxEventRepositoryCustom {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OutboxEventRepositoryImpl(
            @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
            ObjectMapper objectMapper) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<ClaimedOutboxEvent> claimPendingEvents(int limit) {
        return jdbc.query(
                """
                SELECT id, tenant_id, aggregate_type, aggregate_id, event_type, payload, retry_count
                FROM outbox_events
                WHERE published_at IS NULL
                  AND status = 'PENDING'
                  AND (next_attempt_at IS NULL OR next_attempt_at <= NOW())
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> payload;
                    try {
                        String json = rs.getString("payload");
                        payload = objectMapper.readValue(json, PAYLOAD_TYPE);
                    } catch (Exception e) {
                        payload = new LinkedHashMap<>();
                    }
                    return new ClaimedOutboxEvent(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            rs.getString("aggregate_type"),
                            UUID.fromString(rs.getString("aggregate_id")),
                            rs.getString("event_type"),
                            payload,
                            rs.getInt("retry_count"));
                },
                limit);
    }

    @Override
    @Transactional
    public void markPublished(UUID id) {
        jdbc.update(
                """
                UPDATE outbox_events
                SET published_at = NOW(), status = 'PUBLISHED', last_error = NULL, updated_at = NOW()
                WHERE id = ?
                """,
                id);
    }

    @Override
    @Transactional
    public void markFailed(UUID id, int retryCount, String lastError, Instant nextAttemptAt, String status) {
        jdbc.update(
                """
                UPDATE outbox_events
                SET retry_count = ?, last_error = ?, next_attempt_at = ?, status = ?, updated_at = NOW()
                WHERE id = ?
                """,
                retryCount,
                lastError,
                nextAttemptAt != null ? Timestamp.from(nextAttemptAt) : null,
                status,
                id);
    }
}
