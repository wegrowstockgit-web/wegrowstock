package com.invsys.ratelimit;

import com.invsys.core.common.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis token-bucket rate limiter with in-process fallback when Redis is unavailable.
 * Keys: {@code rate:{tenantId}:{system}} or {@code rate:ip:{bucket}:{client}}.
 */
@Component
public class DistributedRateLimiter {

    private static final String LUA = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local tokens = tonumber(ARGV[2])
            local windowMs = tonumber(ARGV[3])
            local now = tonumber(ARGV[4])
            local bucket = redis.call('HMGET', key, 'tokens', 'start')
            local remaining = tonumber(bucket[1])
            local start = tonumber(bucket[2])
            if remaining == nil or start == nil or (now - start) >= windowMs then
              remaining = capacity
              start = now
            end
            if remaining < tokens then
              redis.call('HMSET', key, 'tokens', remaining, 'start', start)
              redis.call('PEXPIRE', key, windowMs)
              return 0
            end
            remaining = remaining - tokens
            redis.call('HMSET', key, 'tokens', remaining, 'start', start)
            redis.call('PEXPIRE', key, windowMs)
            return 1
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA, Long.class);
    private final ConcurrentHashMap<String, LocalWindow> local = new ConcurrentHashMap<>();

    public DistributedRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void tryAcquire(String key, int capacity, int tokens, Duration window) {
        boolean allowed = redis != null
                ? tryRedis(key, capacity, tokens, window)
                : tryLocal(key, capacity, tokens, window);
        if (!allowed) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Rate limit exceeded")
                    .withProperty("type", "about:blank")
                    .withProperty("retryAfterSeconds", window.toSeconds());
        }
    }

    private boolean tryRedis(String key, int capacity, int tokens, Duration window) {
        try {
            Long ok = redis.execute(
                    script,
                    List.of(key),
                    String.valueOf(capacity),
                    String.valueOf(tokens),
                    String.valueOf(window.toMillis()),
                    String.valueOf(System.currentTimeMillis()));
            return ok != null && ok == 1L;
        } catch (RuntimeException ex) {
            return tryLocal(key, capacity, tokens, window);
        }
    }

    private boolean tryLocal(String key, int capacity, int tokens, Duration window) {
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        AtomicInteger holder = new AtomicInteger();
        local.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAtMs >= windowMs) {
                int remaining = capacity - tokens;
                holder.set(remaining >= 0 ? 1 : 0);
                return new LocalWindow(now, new AtomicInteger(Math.max(remaining, 0)));
            }
            if (existing.tokens.get() < tokens) {
                holder.set(0);
                return existing;
            }
            existing.tokens.addAndGet(-tokens);
            holder.set(1);
            return existing;
        });
        return holder.get() == 1;
    }

    /** Test helper. */
    public void resetLocal() {
        local.clear();
    }

    private record LocalWindow(long startedAtMs, AtomicInteger tokens) {
    }
}
