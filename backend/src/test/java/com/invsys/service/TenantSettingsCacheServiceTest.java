package com.invsys.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSettingsCacheServiceTest {

    @Mock ObjectProvider<StringRedisTemplate> redisProvider;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    TenantSettingsCacheService service;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new TenantSettingsCacheService(redisProvider, objectMapper);
    }

    @Test
    void localCacheRoundTripWithoutRedis() {
        when(redisProvider.getIfAvailable()).thenReturn(null);
        UUID tenantId = UUID.randomUUID();
        service.put(tenantId, Map.of("picking_wave_max_lines", 40));
        assertThat(service.get(tenantId)).contains(Map.of("picking_wave_max_lines", 40));
        service.invalidate(tenantId);
        assertThat(service.get(tenantId)).isEmpty();
    }

    @Test
    void redisPathUsesTemplateWhenAvailable() throws Exception {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        UUID tenantId = UUID.randomUUID();
        service.put(tenantId, Map.of("allow_over_receiving", true));
        verify(valueOps).set(eq("invsys:tenant-settings:" + tenantId), anyString(), any(Duration.class));

        String json = objectMapper.writeValueAsString(Map.of("picking_wave_max_lines", 40));
        when(valueOps.get("invsys:tenant-settings:" + tenantId)).thenReturn(json);
        assertThat(service.get(tenantId)).contains(Map.of("picking_wave_max_lines", 40));

        service.invalidate(tenantId);
        verify(redis).delete("invsys:tenant-settings:" + tenantId);
    }

    @Test
    void redisGetFailureFallsBackToLocal() {
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(valueOps);
        UUID tenantId = UUID.randomUUID();
        when(redisProvider.getIfAvailable()).thenReturn(null);
        service.put(tenantId, Map.of("currency", "USD"));
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(service.get(tenantId)).contains(Map.of("currency", "USD"));
    }

    @Test
    void nullTenantIsNoOp() {
        service.put(null, Map.of("x", 1));
        service.invalidate(null);
        assertThat(service.get(null)).isEmpty();
        verify(redisProvider, never()).getIfAvailable();
    }
}
