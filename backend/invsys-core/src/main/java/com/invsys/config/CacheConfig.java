package com.invsys.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-process caches. {@code TenantSettingsCache} holds per-tenant enabled module lists
 * for the {@code @RequireModule} gatekeeper.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TENANT_SETTINGS_CACHE = "TenantSettingsCache";
    public static final String TIER_DEFINITIONS_CACHE = "TierDefinitionsCache";

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(TENANT_SETTINGS_CACHE, TIER_DEFINITIONS_CACHE);
    }
}
