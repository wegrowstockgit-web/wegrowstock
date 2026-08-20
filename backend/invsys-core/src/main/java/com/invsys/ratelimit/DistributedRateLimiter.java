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
 * Kill-switch / custom RPS live at {@code tenant:throttle:{tenantId}}.
 */
@Component
public class DistributedRateLimiter {

    public static final String THROTTLE_KEY_PREFIX = "tenant:throttle:";
    public static final String MULTIPLIER_KEY_PREFIX = "invsys:rate-multiplier:";
    public static final int DEFAULT_TENANT_RPS = 100;

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
            elseif remaining > capacity then
              remaining = capacity
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
    private final ConcurrentHashMap<String, TenantThrottle> tenantThrottles = new ConcurrentHashMap<>();

    public DistributedRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void tryAcquire(String key, int capacity, int tokens, Duration window) {
        rejectIfThrottled(tenantIdFromRateKey(key));
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
     * Multiplier is applied to base capacities for keys matching {@code rate:{tenantId}:*}
     * unless {@code custom_rate_limit} is set.
     */
    public void setTenantCapacityMultiplier(UUID tenantId, double multiplier) {
        if (tenantId == null || multiplier <= 0) {
            return;
        }
        tenantMultipliers.put(tenantId.toString(), multiplier);
        clearTenantBuckets(tenantId);
        if (redis != null) {
            try {
                redis.opsForValue().set(MULTIPLIER_KEY_PREFIX + tenantId, String.valueOf(multiplier));
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
                String raw = redis.opsForValue().get(MULTIPLIER_KEY_PREFIX + tenantId);
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

    public void setTenantThrottle(UUID tenantId, boolean throttled, Integer customRateLimit) {
        if (tenantId == null) {
            return;
        }
        Integer normalized = customRateLimit != null && customRateLimit > 0 ? customRateLimit : null;
        TenantThrottle settings = new TenantThrottle(throttled, normalized);
        tenantThrottles.put(tenantId.toString(), settings);
        clearTenantBuckets(tenantId);
        if (redis != null) {
            try {
                redis.opsForValue().set(THROTTLE_KEY_PREFIX + tenantId, serialize(settings));
            } catch (RuntimeException ignored) {
                // local map remains authoritative
            }
        }
    }

    public TenantThrottle getTenantThrottle(UUID tenantId) {
        if (tenantId == null) {
            return TenantThrottle.OPEN;
        }
        TenantThrottle cached = tenantThrottles.get(tenantId.toString());
        if (cached != null) {
            return cached;
        }
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(THROTTLE_KEY_PREFIX + tenantId);
                if (raw != null) {
                    TenantThrottle parsed = parse(raw);
                    tenantThrottles.put(tenantId.toString(), parsed);
                    return parsed;
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        return TenantThrottle.OPEN;
    }

    public void rejectIfThrottled(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        if (getTenantThrottle(tenantId).throttled()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TRAFFIC_PAUSED",
                    "Tenant traffic is paused by the control plane")
                    .withProperty("type", "about:blank")
                    .withProperty("retryAfterSeconds", 60);
        }
    }

    /** Drop throttle, multiplier, and local buckets for a tenant (GDPR purge / session eviction). */
    public void evictTenant(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        String id = tenantId.toString();
        tenantThrottles.remove(id);
        tenantMultipliers.remove(id);
        clearTenantBuckets(tenantId);
        if (redis != null) {
            try {
                redis.delete(List.of(THROTTLE_KEY_PREFIX + id, MULTIPLIER_KEY_PREFIX + id));
            } catch (RuntimeException ignored) {
                // best-effort
            }
        }
    }

    private int applyOverride(String key, int capacity) {
        UUID tenantId = tenantIdFromRateKey(key);
        if (tenantId == null) {
            return capacity;
        }
        Integer custom = getTenantThrottle(tenantId).customRateLimit();
        if (custom != null) {
            return custom;
        }
        double mult = getTenantCapacityMultiplier(tenantId);
        return Math.max(1, (int) Math.round(capacity * mult));
    }

    static UUID tenantIdFromRateKey(String key) {
        if (key == null || !key.startsWith("rate:") || key.startsWith("rate:ip:")) {
            return null;
        }
        String[] parts = key.split(":", 3);
        if (parts.length < 3) {
            return null;
        }
        try {
            return UUID.fromString(parts[1]);
        } catch (IllegalArgumentException ex) {
            return null;
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
            int available = Math.min(existing.tokens.get(), capacity);
            if (available < tokens) {
                existing.tokens.set(available);
                holder.set(0);
                return existing;
            }
            existing.tokens.set(available - tokens);
            holder.set(1);
            return existing;
        });
        return holder.get() == 1;
    }

    private void clearTenantBuckets(UUID tenantId) {
        String prefix = "rate:" + tenantId + ":";
        local.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Test helper. */
    public void resetLocal() {
        local.clear();
        tenantMultipliers.clear();
        tenantThrottles.clear();
    }

    private static String serialize(TenantThrottle settings) {
        String limit = settings.customRateLimit() == null ? "" : String.valueOf(settings.customRateLimit());
        return (settings.throttled() ? "1" : "0") + "|" + limit;
    }

    private static TenantThrottle parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return TenantThrottle.OPEN;
        }
        int sep = raw.indexOf('|');
        String flag = sep < 0 ? raw : raw.substring(0, sep);
        String limitRaw = sep < 0 ? "" : raw.substring(sep + 1);
        boolean throttled = "1".equals(flag) || "true".equalsIgnoreCase(flag);
        Integer limit = null;
        if (!limitRaw.isBlank()) {
            try {
                int parsed = Integer.parseInt(limitRaw.trim());
                if (parsed > 0) {
                    limit = parsed;
                }
            } catch (NumberFormatException ignored) {
                // keep null
            }
        }
        return new TenantThrottle(throttled, limit);
    }

    private record LocalWindow(long startedAtMs, AtomicInteger tokens) {
    }

    public record TenantThrottle(boolean throttled, Integer customRateLimit) {
        public static final TenantThrottle OPEN = new TenantThrottle(false, null);
    }
}
