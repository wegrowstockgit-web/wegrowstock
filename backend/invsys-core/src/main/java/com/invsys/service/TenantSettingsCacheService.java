package com.invsys.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-through cache for tenant_settings JSON with Redis (when enabled) + local fallback.
 */
@Service
public class TenantSettingsCacheService {

    private static final Logger log = LoggerFactory.getLogger(TenantSettingsCacheService.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "invsys:tenant-settings:";

    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<UUID, Map<String, Object>> local = new ConcurrentHashMap<>();

    public TenantSettingsCacheService(ObjectProvider<StringRedisTemplate> redisTemplate,
                                      ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Map<String, Object>> get(UUID tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(key(tenantId));
                if (json != null && !json.isBlank()) {
                    return Optional.of(objectMapper.readValue(json, new TypeReference<>() {
                    }));
                }
            } catch (Exception ex) {
                log.debug("Redis settings get failed tenant={}: {}", tenantId, ex.getMessage());
            }
        }
        return Optional.ofNullable(local.get(tenantId)).map(Map::copyOf);
    }

    public void put(UUID tenantId, Map<String, Object> settings) {
        if (tenantId == null || settings == null) {
            return;
        }
        Map<String, Object> copy = Map.copyOf(settings);
        local.put(tenantId, copy);
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            try {
                redis.opsForValue().set(key(tenantId), objectMapper.writeValueAsString(copy), TTL);
            } catch (Exception ex) {
                log.debug("Redis settings put failed tenant={}: {}", tenantId, ex.getMessage());
            }
        }
    }

    public void invalidate(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        local.remove(tenantId);
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis != null) {
            try {
                redis.delete(key(tenantId));
            } catch (Exception ex) {
                log.warn("Redis settings invalidate failed tenant={}: {}", tenantId, ex.getMessage());
            }
        }
    }

    private static String key(UUID tenantId) {
        return KEY_PREFIX + tenantId;
    }
}
