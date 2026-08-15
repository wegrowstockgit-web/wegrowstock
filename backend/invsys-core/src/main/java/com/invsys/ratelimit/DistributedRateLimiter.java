package com.invsys.ratelimit;

import com.invsys.core.common.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
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
    private final ConcurrentHashMap<String, Double> tenantMultipliers = new ConcurrentHashMap<>();

    public DistributedRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void tryAcquire(String key, int capacity, int tokens, Duration window) {
        int effectiveCapacity = applyOverride(key, capacity);
        boolean allowed = redis != null
                ? tryRedis(key, effectiveCapacity, tokens, window)
                : tryLocal(key, effectiveCapacity, tokens, window);
        if (!allowed) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Rate limit exceeded")
                    .withProperty("type", "about:blank")
                    .withProperty("retryAfterSeconds", window.toSeconds());
        }
    }

    /**
     * Live token-bucket capacity override for a tenant (noisy-neighbor control).
     * Multiplier is applied to base capacities for keys matching {@code rate:{tenantId}:*}.
     */
    public void setTenantCapacityMultiplier(UUID tenantId, double multiplier) {
        if (tenantId == null || multiplier <= 0) {
            return;
        }
        tenantMultipliers.put(tenantId.toString(), multiplier);
        if (redis != null) {
            try {
                redis.opsForValue().set("invsys:rate-multiplier:" + tenantId, String.valueOf(multiplier));
            } catch (RuntimeException ignored) {
                // local map remains authoritative for this process
            }
        }
    }

    public double getTenantCapacityMultiplier(UUID tenantId) {
        if (tenantId == null) {
            return 1.0;
        }
        Double cached = tenantMultipliers.get(tenantId.toString());
        if (cached != null) {
            return cached;
        }
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get("invsys:rate-multiplier:" + tenantId);
                if (raw != null) {
                    double parsed = Double.parseDouble(raw);
                    tenantMultipliers.put(tenantId.toString(), parsed);
                    return parsed;
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return 1.0;
    }

    private int applyOverride(String key, int capacity) {
        if (key == null || !key.startsWith("rate:") || key.startsWith("rate:ip:")) {
            return capacity;
        }
        String[] parts = key.split(":", 3);
        if (parts.length < 3) {
            return capacity;
        }
        try {
            double mult = getTenantCapacityMultiplier(UUID.fromString(parts[1]));
            return Math.max(1, (int) Math.round(capacity * mult));
        } catch (IllegalArgumentException ex) {
            return capacity;
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
        tenantMultipliers.clear();
    }

    private record LocalWindow(long startedAtMs, AtomicInteger tokens) {
    }
}
