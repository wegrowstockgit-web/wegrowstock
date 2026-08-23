package com.invsys.core.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side desktop idle lock so a refresh cannot clear the overlay.
 * Redis-backed with an in-memory fallback for tests.
 */
@Component
public class DesktopSessionLockStore {

    static final Duration LOCK_TTL = Duration.ofHours(12);
    private static final String KEY_PREFIX = "desktop-lock:";

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, Long> local = new ConcurrentHashMap<>();

    public DesktopSessionLockStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void lock(UUID userId) {
        if (userId == null) {
            return;
        }
        String key = key(userId);
        long expiresAt = System.currentTimeMillis() + LOCK_TTL.toMillis();
        if (redis != null) {
            redis.opsForValue().set(key, "1", LOCK_TTL);
            return;
        }
        local.put(key, expiresAt);
    }

    public void unlock(UUID userId) {
        if (userId == null) {
            return;
        }
        String key = key(userId);
        if (redis != null) {
            redis.delete(key);
        }
        local.remove(key);
    }

    public boolean isLocked(UUID userId) {
        if (userId == null) {
            return false;
        }
        String key = key(userId);
        if (redis != null) {
            Boolean present = redis.hasKey(key);
            return Boolean.TRUE.equals(present);
        }
        Long expiresAt = local.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            local.remove(key);
            return false;
        }
        return true;
    }

    public void reset() {
        local.clear();
    }

    private static String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
