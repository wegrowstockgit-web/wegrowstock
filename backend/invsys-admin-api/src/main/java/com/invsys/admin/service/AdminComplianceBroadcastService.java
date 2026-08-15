package com.invsys.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invsys.core.common.ApiException;
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
import java.util.Map;
import java.util.UUID;

/**
 * Platform compliance broadcasts. Source of truth is {@code platform_compliance_broadcasts};
 * WMS TaxScheme / ComplianceLot readers can poll this table when present.
 * On activate we also fan-out a {@code compliance.broadcasts} key into {@code tenant_settings.settings} jsonb.
 */
@Service
public class AdminComplianceBroadcastService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminComplianceBroadcastService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
    }

    public List<BroadcastView> list() {
        return jdbc.query(
                """
                SELECT id, category, title, payload_json::text, active, created_at
                FROM platform_compliance_broadcasts
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> new BroadcastView(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("category"),
                        rs.getString("title"),
                        rs.getString("payload_json"),
                        rs.getBoolean("active"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public BroadcastView create(CreateBroadcastRequest request) {
        UUID id = UUID.randomUUID();
        UUID createdBy = currentAdminId();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(
                    request.payload() == null ? Map.of() : request.payload());
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "payload must be JSON-serializable");
        }
        jdbc.update("""
                INSERT INTO platform_compliance_broadcasts (id, category, title, payload_json, active, created_at, created_by)
                VALUES (?, ?, ?, CAST(? AS jsonb), TRUE, NOW(), ?)
                """,
                id, request.category(), request.title(), payloadJson, createdBy);
        return list().stream().filter(b -> b.id().equals(id)).findFirst()
                .orElse(new BroadcastView(id, request.category(), request.title(), payloadJson, true, Instant.now()));
    }

    @Transactional
    public BroadcastView activate(UUID id) {
        int updated = jdbc.update("""
                UPDATE platform_compliance_broadcasts SET active = TRUE WHERE id = ?
                """, id);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Broadcast not found");
        }

        // Fan-out hint into tenant_settings so WMS can read without a new API yet.
        jdbc.update("""
                UPDATE tenant_settings
                SET settings = jsonb_set(
                        COALESCE(settings, '{}'::jsonb),
                        '{compliance,broadcasts}',
                        COALESCE(
                            (SELECT jsonb_agg(jsonb_build_object(
                                'id', id,
                                'category', category,
                                'title', title,
                                'payload', payload_json
                             ))
                             FROM platform_compliance_broadcasts
                             WHERE active = TRUE),
                            '[]'::jsonb
                        ),
                        true
                    ),
                    updated_at = NOW()
                """);

        return list().stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Broadcast not found"));
    }

    private static UUID currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    public record CreateBroadcastRequest(String category, String title, Map<String, Object> payload) {
    }

    public record BroadcastView(
            UUID id,
            String category,
            String title,
            String payloadJson,
            boolean active,
            Instant createdAt
    ) {
    }
}
