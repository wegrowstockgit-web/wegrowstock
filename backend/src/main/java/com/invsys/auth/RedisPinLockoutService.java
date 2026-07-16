package com.invsys.auth;

import com.invsys.common.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window PIN lockout backed by Redis (in-memory fallback).
 * Default rule: 3 failures within 5 minutes → lock credential for 15 minutes.
 */
@Component
public class RedisPinLockoutService {

    public static final int MAX_FAILURES = 3;
    public static final Duration FAIL_WINDOW = Duration.ofMinutes(5);
    public static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, LocalState> local = new ConcurrentHashMap<>();

    public RedisPinLockoutService(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public void assertAllowed(String credentialKey) {
        assertAllowed(credentialKey, MAX_FAILURES);
    }

    public void assertAllowed(String credentialKey, int maxFailures) {
        Instant unlockAt = readUnlockAt(credentialKey);
        if (unlockAt != null && unlockAt.isAfter(Instant.now())) {
            throw locked(unlockAt);
        }
    }

    public void recordFailure(String credentialKey) {
        recordFailure(credentialKey, MAX_FAILURES);
    }

    public void recordFailure(String credentialKey, int maxFailures) {
        Instant now = Instant.now();
        Instant unlockAt = readUnlockAt(credentialKey);
        if (unlockAt != null && unlockAt.isAfter(now)) {
            throw locked(unlockAt);
        }

        int failures = bumpFailures(credentialKey, now);
        if (failures >= maxFailures) {
            Instant until = now.plus(LOCKOUT);
            writeUnlockAt(credentialKey, until);
            clearFailures(credentialKey);
            throw locked(until);
        }
    }

    public void recordSuccess(String credentialKey) {
        clearFailures(credentialKey);
        clearUnlock(credentialKey);
    }

    public void reset() {
        local.clear();
    }

    private ApiException locked(Instant unlockAt) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "PIN_LOCKED",
                "Too many invalid PIN attempts — unlocks at " + unlockAt)
                .withProperty("unlockAt", unlockAt.toString())
                .withProperty("unlockAtEpochMs", unlockAt.toEpochMilli());
    }

    private Instant readUnlockAt(String credentialKey) {
        String key = lockKey(credentialKey);
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(key);
                if (raw != null && !raw.isBlank()) {
                    return Instant.ofEpochMilli(Long.parseLong(raw));
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        LocalState state = local.get(credentialKey);
        return state != null ? state.unlockAt : null;
    }

    private void writeUnlockAt(String credentialKey, Instant until) {
        String key = lockKey(credentialKey);
        if (redis != null) {
            try {
                redis.opsForValue().set(key, String.valueOf(until.toEpochMilli()), LOCKOUT);
                return;
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        local.compute(credentialKey, (k, existing) -> {
            LocalState next = existing != null ? existing : new LocalState();
            next.unlockAt = until;
            next.failures = 0;
            next.windowStart = null;
            return next;
        });
    }

    private int bumpFailures(String credentialKey, Instant now) {
        String key = failKey(credentialKey);
        if (redis != null) {
            try {
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1L) {
                    redis.expire(key, FAIL_WINDOW);
                }
                return count == null ? 1 : count.intValue();
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        AtomicHolder holder = new AtomicHolder();
        local.compute(credentialKey, (k, existing) -> {
            LocalState next = existing != null ? existing : new LocalState();
            if (next.windowStart == null
                    || Duration.between(next.windowStart, now).compareTo(FAIL_WINDOW) >= 0) {
                next.windowStart = now;
                next.failures = 1;
            } else {
                next.failures += 1;
            }
            holder.value = next.failures;
            return next;
        });
        return holder.value;
    }

    private void clearFailures(String credentialKey) {
        if (redis != null) {
            try {
                redis.delete(failKey(credentialKey));
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
        LocalState state = local.get(credentialKey);
        if (state != null) {
            state.failures = 0;
            state.windowStart = null;
        }
    }

    private void clearUnlock(String credentialKey) {
        if (redis != null) {
            try {
                redis.delete(lockKey(credentialKey));
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
        LocalState state = local.get(credentialKey);
        if (state != null) {
            state.unlockAt = null;
        }
    }

    private static String failKey(String credentialKey) {
        return "pin:fail:" + credentialKey;
    }

    private static String lockKey(String credentialKey) {
        return "pin:lock:" + credentialKey;
    }

    private static final class LocalState {
        int failures;
        Instant windowStart;
        Instant unlockAt;
    }

    private static final class AtomicHolder {
        int value;
    }
}
