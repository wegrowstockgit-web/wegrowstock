package com.invsys.core.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-use store for impersonation JWT ids ({@code jti}) and opaque handoff codes.
 * Redis when available; in-process fallback for tests / local without Redis.
 */
@Component
public class ImpersonationHandoffStore {

    private static final String JTI_PREFIX = "impersonation:jti:";
    private static final String CODE_PREFIX = "impersonation:code:";

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, Entry> local = new ConcurrentHashMap<>();

    public ImpersonationHandoffStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void register(String jti, String handoffCode, String accessToken, Duration ttl) {
        Duration safeTtl = ttl == null || ttl.isZero() || ttl.isNegative()
                ? Duration.ofMinutes(15)
                : ttl;
        put(JTI_PREFIX + jti, "1", safeTtl);
        put(CODE_PREFIX + handoffCode, accessToken, safeTtl);
    }

    /**
     * Atomically consume a {@code jti}. Returns {@code false} if missing or already used.
     */
    public boolean consumeJti(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return consumeKey(JTI_PREFIX + jti);
    }

    public Optional<String> redeemCode(String handoffCode) {
        if (handoffCode == null || handoffCode.isBlank()) {
            return Optional.empty();
        }
        String token = consumeAndGet(CODE_PREFIX + handoffCode);
        return token == null || token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    /** Test helper. */
    public void resetLocal() {
        local.clear();
    }

    private void put(String key, String value, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        local.put(key, new Entry(value, expiresAt));
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (RuntimeException ignored) {
            // local map remains authoritative
        }
    }

    private boolean consumeKey(String key) {
        Entry removed = local.remove(key);
        boolean localHit = removed != null && removed.expiresAt > System.currentTimeMillis();
        if (redis == null) {
            return localHit;
        }
        try {
            Boolean deleted = redis.delete(key);
            return Boolean.TRUE.equals(deleted) || localHit;
        } catch (RuntimeException ex) {
            return localHit;
        }
    }

    private String consumeAndGet(String key) {
        Entry removed = local.remove(key);
        String localValue = removed != null && removed.expiresAt > System.currentTimeMillis()
                ? removed.value
                : null;
        if (redis == null) {
            return localValue;
        }
        try {
            String remote = redis.opsForValue().get(key);
            if (remote != null) {
                redis.delete(key);
                return remote;
            }
        } catch (RuntimeException ignored) {
            // fall through to local
        }
        return localValue;
    }

    private record Entry(String value, long expiresAt) {
    }
}
