package com.invsys.service;

import com.invsys.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Evicts the in-process enabled-modules cache and broadcasts to other WMS / admin
 * nodes over Redis. Control-plane writes happen in {@code invsys-admin-api};
 * {@code @CacheEvict} alone cannot reach the WMS JVM.
 */
@Service
public class TenantEnabledModulesCacheService {

    public static final String REDIS_CHANNEL = "invsys:tenant-modules:evict";

    private static final Logger log = LoggerFactory.getLogger(TenantEnabledModulesCacheService.class);

    private final CacheManager cacheManager;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;

    public TenantEnabledModulesCacheService(CacheManager cacheManager,
                                            ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    public void evict(UUID tenantId) {
        evictLocal(tenantId);
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null || tenantId == null) {
            return;
        }
        try {
            redis.convertAndSend(REDIS_CHANNEL, tenantId.toString());
        } catch (Exception ex) {
            log.warn("Enabled-modules cache broadcast failed tenant={}: {}", tenantId, ex.getMessage());
        }
    }

    public void evictLocal(UUID tenantId) {
        Cache cache = cacheManager.getCache(CacheConfig.TENANT_SETTINGS_CACHE);
        if (cache == null) {
            return;
        }
        if (tenantId == null) {
            cache.clear();
            return;
        }
        cache.evict(tenantId);
    }
}
