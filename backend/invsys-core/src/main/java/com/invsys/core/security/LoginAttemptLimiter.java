package com.invsys.core.security;

import com.invsys.core.common.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Failed-login lockout for the WMS data plane (per IP and per email).
 */
@Component
public class LoginAttemptLimiter {

    public static final String RATE_LIMIT_CODE = "AUTH_RATE_LIMIT_EXCEEDED";

    private final StringRedisTemplate redis;
    private final int maxFailures;
    private final Duration window;
    private final ConcurrentHashMap<String, List<Long>> local = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${invsys.security.login-max-failures:5}") int maxFailures,
            @Value("${invsys.security.login-window-minutes:10}") int windowMinutes) {
        this.redis = redisProvider.getIfAvailable();
        this.maxFailures = Math.max(1, maxFailures);
        this.window = Duration.ofMinutes(Math.max(1, windowMinutes));
    }

    public void assertAllowed(String ip, String email) {
        if (count(ipKey(ip)) >= maxFailures || count(emailKey(email)) >= maxFailures) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_CODE,
                    "Too many failed login attempts. Try again later.")
                    .withProperty("retryAfterSeconds", window.toSeconds());
        }
    }

    public void recordFailure(String ip, String email) {
        increment(ipKey(ip));
        increment(emailKey(email));
    }

    public void reset(String ip, String email) {
        clear(ipKey(ip));
        clear(emailKey(email));
    }

    public void resetLocal() {
        local.clear();
    }

    private int count(String key) {
        prune(key);
        List<Long> hits = local.get(key);
        int localCount = hits == null ? 0 : hits.size();
        if (redis == null) {
            return localCount;
        }
        try {
            Long now = System.currentTimeMillis();
            redis.opsForZSet().removeRangeByScore(key, 0, now - window.toMillis());
            Long remote = redis.opsForZSet().zCard(key);
            return remote == null ? localCount : remote.intValue();
        } catch (RuntimeException ex) {
            return localCount;
        }
    }

    private void increment(String key) {
        long now = System.currentTimeMillis();
        local.compute(key, (k, existing) -> {
            List<Long> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.removeIf(ts -> now - ts >= window.toMillis());
            next.add(now);
            return next;
        });
        if (redis == null) {
            return;
        }
        try {
            String member = now + ":" + Math.random();
            redis.opsForZSet().add(key, member, now);
            redis.opsForZSet().removeRangeByScore(key, 0, now - window.toMillis());
            redis.expire(key, window);
        } catch (RuntimeException ignored) {
            // local map remains authoritative
        }
    }

    private void clear(String key) {
        local.remove(key);
        if (redis == null) {
            return;
        }
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // local clear is enough
        }
    }

    private void prune(String key) {
        long now = System.currentTimeMillis();
        local.computeIfPresent(key, (k, existing) -> {
            List<Long> next = new ArrayList<>(existing);
            next.removeIf(ts -> now - ts >= window.toMillis());
            return next.isEmpty() ? null : next;
        });
    }

    private static String ipKey(String ip) {
        String value = ip == null || ip.isBlank() ? "unknown" : ip.trim();
        return "wms-login:ip:" + value;
    }

    private static String emailKey(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return "wms-login:email:" + value;
    }
}
