package com.invsys.service;

import com.invsys.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Evicts the in-process tier-bundle cache and broadcasts to other WMS / admin
 * nodes over Redis so packaging edits take effect immediately.
 */
@Service
public class PlatformTierDefinitionCacheService {

    public static final String REDIS_CHANNEL = "invsys:tier-definitions:evict";

    private static final Logger log = LoggerFactory.getLogger(PlatformTierDefinitionCacheService.class);

    private final CacheManager cacheManager;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;

    public PlatformTierDefinitionCacheService(CacheManager cacheManager,
                                              ObjectProvider<StringRedisTemplate> redisTemplate) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    public void evictAll() {
        evictLocal();
        StringRedisTemplate redis = redisTemplate.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.convertAndSend(REDIS_CHANNEL, "all");
        } catch (Exception ex) {
            log.warn("Tier-definition cache broadcast failed: {}", ex.getMessage());
        }
    }

    public void evictLocal() {
        Cache cache = cacheManager.getCache(CacheConfig.TIER_DEFINITIONS_CACHE);
        if (cache != null) {
            cache.clear();
        }
    }
}
