package com.invsys.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Pings Redis and records round-trip latency. When Redis is disabled, reports UP/disabled
 * so local tests and single-node runs stay healthy.
 */
@Component("redisPing")
public class RedisPingHealthIndicator implements HealthIndicator {

    private final ObjectProvider<RedisConnectionFactory> connectionFactory;
    private final boolean redisEnabled;

    public RedisPingHealthIndicator(ObjectProvider<RedisConnectionFactory> connectionFactory,
                                    @Value("${invsys.redis.enabled:false}") boolean redisEnabled) {
        this.connectionFactory = connectionFactory;
        this.redisEnabled = redisEnabled;
    }

    @Override
    public Health health() {
        if (!redisEnabled) {
            return Health.up().withDetail("enabled", false).withDetail("mode", "in-memory-fallback").build();
        }
        RedisConnectionFactory factory = connectionFactory.getIfAvailable();
        if (factory == null) {
            return Health.down().withDetail("enabled", true).withDetail("error", "Redis beans missing").build();
        }
        long started = System.nanoTime();
        try (RedisConnection connection = factory.getConnection()) {
            String pong = connection.ping();
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("ping", pong != null ? pong : "PONG")
                    .withDetail("latencyMs", latencyMs)
                    .build();
        } catch (Exception ex) {
            long latencyMs = (System.nanoTime() - started) / 1_000_000L;
            return Health.down(ex)
                    .withDetail("enabled", true)
                    .withDetail("latencyMs", latencyMs)
                    .build();
        }
    }
}