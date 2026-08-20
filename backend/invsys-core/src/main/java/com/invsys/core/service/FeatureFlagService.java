package com.invsys.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Progressive-delivery flags with Redis cache ({@code flags:{flagKey}:{tenantId}}, 60s TTL).
 */
@Service
public class FeatureFlagService {

    public static final String CACHE_PREFIX = "flags:";
    public static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, CacheEntry> local = new ConcurrentHashMap<>();

    public FeatureFlagService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                              ObjectProvider<StringRedisTemplate> redisProvider) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.redis = redisProvider.getIfAvailable();
    }

    public boolean isEnabled(String flagKey, UUID tenantId) {
        if (flagKey == null || flagKey.isBlank() || tenantId == null) {
            return false;
        }
        String cacheKey = cacheKey(flagKey, tenantId);
        Boolean cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        boolean enabled = loadEnabled(flagKey.trim(), tenantId);
        writeCache(cacheKey, enabled);
        return enabled;
    }

    public List<String> listEnabledKeys(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return jdbc.query(
                """
                SELECT f.flag_key
                  FROM feature_flags f
                  LEFT JOIN tenant_feature_flags t
                    ON t.flag_id = f.id AND t.tenant_id = ?
                 WHERE COALESCE(t.enabled, f.is_global) = TRUE
                 ORDER BY f.flag_key
                """,
                (rs, rowNum) -> rs.getString("flag_key"),
                tenantId);
    }

    public List<FlagView> listFlags() {
        Map<UUID, List<TenantOverrideView>> overrides = new java.util.LinkedHashMap<>();
        jdbc.query(
                """
                SELECT flag_id, tenant_id, enabled
                  FROM tenant_feature_flags
                 ORDER BY flag_id, tenant_id
                """,
                rs -> {
                    UUID flagId = UUID.fromString(rs.getString("flag_id"));
                    overrides.computeIfAbsent(flagId, ignored -> new ArrayList<>())
                            .add(new TenantOverrideView(
                                    UUID.fromString(rs.getString("tenant_id")),
                                    rs.getBoolean("enabled")));
                });
        return jdbc.query(
                """
                SELECT id, flag_key, description, is_global, created_at
                  FROM feature_flags
                 ORDER BY flag_key
                """,
                (rs, rowNum) -> {
                    UUID id = UUID.fromString(rs.getString("id"));
                    return new FlagView(
                            id,
                            rs.getString("flag_key"),
                            rs.getString("description"),
                            rs.getBoolean("is_global"),
                            rs.getTimestamp("created_at").toInstant(),
                            overrides.getOrDefault(id, List.of()));
                });
    }

    @Transactional
    public FlagView createFlag(String flagKey, String description, boolean isGlobal) {
        String key = flagKey == null ? "" : flagKey.trim();
        if (key.isEmpty() || key.length() > 64) {
            throw new com.invsys.core.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_FLAG_KEY",
                    "flagKey is required (max 64 characters)");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO feature_flags (id, flag_key, description, is_global, created_at)
                VALUES (?, ?, ?, ?, NOW())
                """, id, key, description, isGlobal);
        evictFlag(key);
        return getFlag(id);
    }

    @Transactional
    public FlagView replaceTenantOverrides(UUID flagId, Boolean isGlobal, List<TenantOverrideView> overrides) {
        FlagView existing = getFlag(flagId);
        if (isGlobal != null) {
            jdbc.update("UPDATE feature_flags SET is_global = ? WHERE id = ?", isGlobal, flagId);
        }
        jdbc.update("DELETE FROM tenant_feature_flags WHERE flag_id = ?", flagId);
        if (overrides != null) {
            for (TenantOverrideView row : overrides) {
                if (row == null || row.tenantId() == null) {
                    continue;
                }
                jdbc.update("""
                        INSERT INTO tenant_feature_flags (tenant_id, flag_id, enabled)
                        VALUES (?, ?, ?)
                        ON CONFLICT (tenant_id, flag_id) DO UPDATE SET enabled = EXCLUDED.enabled
                        """, row.tenantId(), flagId, row.enabled());
            }
        }
        evictFlag(existing.flagKey());
        return getFlag(flagId);
    }

    public void evictTenant(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        String suffix = ":" + tenantId;
        local.keySet().removeIf(key -> key.endsWith(suffix));
        if (redis == null) {
            return;
        }
        try {
            scanDelete(CACHE_PREFIX + "*:" + tenantId);
        } catch (RuntimeException ex) {
            log.warn("Feature-flag cache eviction failed tenant={}: {}", tenantId, ex.getMessage());
        }
    }

    /** Test helper. */
    public void resetLocal() {
        local.clear();
    }

    private FlagView getFlag(UUID flagId) {
        return listFlags().stream()
                .filter(flag -> flag.id().equals(flagId))
                .findFirst()
                .orElseThrow(() -> new com.invsys.core.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "FLAG_NOT_FOUND", "Feature flag not found"));
    }

    private boolean loadEnabled(String flagKey, UUID tenantId) {
        Boolean enabled = jdbc.query(
                """
                SELECT COALESCE(t.enabled, f.is_global) AS enabled
                  FROM feature_flags f
                  LEFT JOIN tenant_feature_flags t
                    ON t.flag_id = f.id AND t.tenant_id = ?
                 WHERE f.flag_key = ?
                """,
                rs -> rs.next() && rs.getBoolean("enabled"),
                tenantId, flagKey);
        return Boolean.TRUE.equals(enabled);
    }

    private Boolean readCache(String cacheKey) {
        CacheEntry localHit = local.get(cacheKey);
        if (localHit != null && localHit.expiresAtMs > System.currentTimeMillis()) {
            return localHit.enabled;
        }
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(cacheKey);
                if (raw != null) {
                    boolean enabled = "1".equals(raw) || "true".equalsIgnoreCase(raw);
                    local.put(cacheKey, new CacheEntry(enabled, System.currentTimeMillis() + CACHE_TTL.toMillis()));
                    return enabled;
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return null;
    }

    private void writeCache(String cacheKey, boolean enabled) {
        local.put(cacheKey, new CacheEntry(enabled, System.currentTimeMillis() + CACHE_TTL.toMillis()));
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(cacheKey, enabled ? "1" : "0", CACHE_TTL);
        } catch (RuntimeException ignored) {
            // local remains authoritative
        }
    }

    private void evictFlag(String flagKey) {
        String prefix = CACHE_PREFIX + flagKey + ":";
        local.keySet().removeIf(key -> key.startsWith(prefix));
        if (redis == null) {
            return;
        }
        try {
            scanDelete(prefix + "*");
        } catch (RuntimeException ex) {
            log.warn("Feature-flag cache eviction failed flag={}: {}", flagKey, ex.getMessage());
        }
    }

    private void scanDelete(String pattern) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(256).build();
        Set<String> keys = new HashSet<>();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static String cacheKey(String flagKey, UUID tenantId) {
        return CACHE_PREFIX + flagKey + ":" + tenantId;
    }

    private record CacheEntry(boolean enabled, long expiresAtMs) {
    }

    public record FlagView(
            UUID id,
            String flagKey,
            String description,
            boolean isGlobal,
            Instant createdAt,
            List<TenantOverrideView> tenants
    ) {
    }

    public record TenantOverrideView(UUID tenantId, boolean enabled) {
    }
}
