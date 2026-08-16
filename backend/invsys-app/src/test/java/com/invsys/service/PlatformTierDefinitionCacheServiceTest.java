package com.invsys.service;

import com.invsys.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformTierDefinitionCacheServiceTest {

    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    @Mock ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock StringRedisTemplate redis;

    @Test
    void evictAllClearsLocalCacheAndBroadcasts() {
        when(cacheManager.getCache(CacheConfig.TIER_DEFINITIONS_CACHE)).thenReturn(cache);
        when(redisProvider.getIfAvailable()).thenReturn(redis);

        new PlatformTierDefinitionCacheService(cacheManager, redisProvider).evictAll();

        verify(cache).clear();
        verify(redis).convertAndSend(PlatformTierDefinitionCacheService.REDIS_CHANNEL, "all");
    }
}
