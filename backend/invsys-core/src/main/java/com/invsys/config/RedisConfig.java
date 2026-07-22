package com.invsys.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Redis is opt-in via {@code invsys.redis.enabled=true}. When disabled, rate-limit
 * and PIN lockout services fall back to process-local state.
 */
@Configuration
@ConditionalOnProperty(name = "invsys.redis.enabled", havingValue = "true")
@Import(DataRedisAutoConfiguration.class)
public class RedisConfig {
}
