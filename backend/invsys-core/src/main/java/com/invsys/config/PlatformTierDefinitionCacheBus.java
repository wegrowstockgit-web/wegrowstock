package com.invsys.config;

import com.invsys.service.PlatformTierDefinitionCacheService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Subscribes WMS / admin nodes to packaging-cache eviction broadcasts.
 */
@Configuration
@ConditionalOnProperty(name = "invsys.redis.enabled", havingValue = "true")
@ConditionalOnBean(RedisConnectionFactory.class)
public class PlatformTierDefinitionCacheBus {

    @Bean
    RedisMessageListenerContainer platformTierDefinitionCacheListener(
            RedisConnectionFactory connectionFactory,
            PlatformTierDefinitionCacheService cacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener = (message, pattern) -> cacheService.evictLocal();
        container.addMessageListener(listener, new ChannelTopic(PlatformTierDefinitionCacheService.REDIS_CHANNEL));
        return container;
    }
}
