package com.invsys.service;

import com.invsys.domain.AuditLog;
import com.invsys.domain.User;
import com.invsys.repository.AuditLogRepository;
import com.invsys.repository.UserRepository;
import com.invsys.tenancy.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuditQueryService {

    private static final int MAX_LIMIT = 200;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditQueryService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<EnrichedAudit> forEntity(String entityType, UUID entityId, int limit) {
        UUID tenantId = TenantContext.requireTenantId();
        int pageSize = clamp(limit);
        List<String> types = expandEntityTypes(entityType);
        List<AuditLog> rows = auditLogRepository
                .findByTenantIdAndEntityTypeInAndEntityIdOrderByCreatedAtDescIdDesc(
                        tenantId, types, entityId, PageRequest.of(0, pageSize));
        return enrich(rows);
    }

    @Transactional(readOnly = true)
    public PageResult forTenant(String cursor, int limit, String entityType, String action) {
        UUID tenantId = TenantContext.requireTenantId();
        int pageSize = clamp(limit);
        Cursor parsed = Cursor.parse(cursor);
        String typeFilter = blankToNull(entityType);
        String actionFilter = blankToNull(action);
        if (typeFilter != null) {
            typeFilter = typeFilter.trim().toUpperCase(Locale.ROOT);
        }
        if (actionFilter != null) {
            actionFilter = actionFilter.trim().toUpperCase(Locale.ROOT);
        }

        PageRequest pageable = PageRequest.of(0, pageSize + 1);
        List<AuditLog> rows = parsed == null
                ? auditLogRepository.findTenantFirstPage(tenantId, typeFilter, actionFilter, pageable)
                : auditLogRepository.findTenantAfterCursor(
                        tenantId, typeFilter, actionFilter, parsed.createdAt(), parsed.id(), pageable);

        boolean hasMore = rows.size() > pageSize;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, pageSize));
        }
        List<EnrichedAudit> items = enrich(rows);
        String next = null;
        if (hasMore && !rows.isEmpty()) {
            AuditLog last = rows.getLast();
            next = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }
        return new PageResult(items, next);
    }

    private List<EnrichedAudit> enrich(List<AuditLog> rows) {
        Set<UUID> actorIds = rows.stream()
                .map(AuditLog::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> actors = new HashMap<>();
        if (!actorIds.isEmpty()) {
            for (User user : userRepository.findAllById(actorIds)) {
                actors.put(user.getId(), user);
            }
        }
        List<EnrichedAudit> out = new ArrayList<>(rows.size());
        for (AuditLog row : rows) {
            User actor = row.getActorUserId() != null ? actors.get(row.getActorUserId()) : null;
            Map<String, Object> diff = row.getDiff() != null
                    ? new LinkedHashMap<>(row.getDiff())
                    : Map.of();
            out.add(new EnrichedAudit(
                    row.getId(),
                    row.getActorUserId(),
                    actor != null ? actor.getEmail() : null,
                    actor != null ? actor.getDisplayName() : null,
                    row.getAction(),
                    row.getEntityType(),
                    row.getEntityId(),
                    diff,
                    row.getCreatedAt()));
        }
        return out;
    }

    private static List<String> expandEntityTypes(String entityType) {
        String key = entityType == null ? "" : entityType.trim().toUpperCase(Locale.ROOT);
        if (key.equals("USER") || key.equals("USERS")) {
            return List.of("USER", "USERS");
        }
        return List.of(key);
    }

    private static int clamp(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public record EnrichedAudit(
            UUID id,
            UUID actorUserId,
            String actorEmail,
            String actorDisplayName,
            String action,
            String entityType,
            UUID entityId,
            Map<String, Object> diff,
            Instant createdAt
    ) {
    }

    public record PageResult(List<EnrichedAudit> items, String nextCursor) {
    }

    record Cursor(Instant createdAt, UUID id) {
        static Cursor parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\|", 2);
                if (parts.length != 2) {
                    return null;
                }
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (Exception ex) {
                return null;
            }
        }

        String encode() {
            String payload = createdAt + "|" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        }
    }
}
