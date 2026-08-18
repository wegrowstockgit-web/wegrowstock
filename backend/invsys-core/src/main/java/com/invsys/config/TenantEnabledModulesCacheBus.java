package com.invsys.config;

import com.invsys.service.TenantEnabledModulesCacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Subscribes WMS / admin nodes to enabled-module cache eviction broadcasts
 * from the other plane (admin PATCH → WMS {@code /me} + {@code @RequireModule}).
 */
@Configuration
@ConditionalOnProperty(name = "invsys.redis.enabled", havingValue = "true")
@ConditionalOnBean(RedisConnectionFactory.class)
public class TenantEnabledModulesCacheBus {

    @Bean
    RedisMessageListenerContainer tenantEnabledModulesCacheListener(
            RedisConnectionFactory connectionFactory,
            TenantEnabledModulesCacheService cacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener = (message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8).trim();
            try {
                cacheService.evictLocal(UUID.fromString(body));
            } catch (IllegalArgumentException ex) {
                cacheService.evictLocal(null);
            }
        };
        container.addMessageListener(listener, new ChannelTopic(TenantEnabledModulesCacheService.REDIS_CHANNEL));
        return container;
    }
}
