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

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantEnabledModulesCacheServiceTest {

    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    @Mock ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock StringRedisTemplate redis;

    @Test
    void evictClearsLocalEntryAndBroadcastsTenantId() {
        UUID tenantId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        when(cacheManager.getCache(CacheConfig.TENANT_SETTINGS_CACHE)).thenReturn(cache);
        when(redisProvider.getIfAvailable()).thenReturn(redis);

        new TenantEnabledModulesCacheService(cacheManager, redisProvider).evict(tenantId);

        verify(cache).evict(tenantId);
        verify(redis).convertAndSend(TenantEnabledModulesCacheService.REDIS_CHANNEL, tenantId.toString());
    }
}
