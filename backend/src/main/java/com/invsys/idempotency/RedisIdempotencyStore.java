package com.invsys.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding 24-hour idempotency cache keyed by {@code idempotency:{tenantId}:{key}}.
 * Prefers Redis when enabled; falls back to an in-process map for tests / single-node.
 */
@Component
public class RedisIdempotencyStore {

    public static final Duration TTL = Duration.ofHours(24);
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);

    private final StringRedisTemplate redis;
    private final ConcurrentHashMap<String, LocalEntry> local = new ConcurrentHashMap<>();

    public RedisIdempotencyStore(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
    }

    public Optional<CachedResponse> get(UUID tenantId, String idempotencyKey) {
        String redisKey = redisKey(tenantId, idempotencyKey);
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(redisKey);
                if (raw != null) {
                    redis.expire(redisKey, TTL); // sliding 24h window
                    return Optional.of(decode(raw));
                }
                return Optional.empty();
            } catch (RuntimeException ex) {
                log.debug("Redis idempotency get failed: {}", ex.getMessage());
            }
        }
        LocalEntry entry = local.get(redisKey);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            if (entry != null) {
                local.remove(redisKey, entry);
            }
            return Optional.empty();
        }
        // Sliding window: touch TTL on read.
        local.put(redisKey, new LocalEntry(entry.payload(), Instant.now().plus(TTL)));
        return Optional.of(entry.payload());
    }

    public void put(UUID tenantId, String idempotencyKey, CachedResponse response) {
        String redisKey = redisKey(tenantId, idempotencyKey);
        String encoded = encode(response);
        if (redis != null) {
            try {
                // SET NX + EX — first writer wins; subsequent puts refresh TTL (sliding).
                Boolean created = redis.opsForValue().setIfAbsent(redisKey, encoded, TTL);
                if (Boolean.FALSE.equals(created)) {
                    redis.expire(redisKey, TTL);
                }
                return;
            } catch (RuntimeException ex) {
                log.debug("Redis idempotency put failed: {}", ex.getMessage());
            }
        }
        local.put(redisKey, new LocalEntry(response, Instant.now().plus(TTL)));
    }

    static String redisKey(UUID tenantId, String idempotencyKey) {
        return "idempotency:" + tenantId + ":" + idempotencyKey.trim();
    }

    static String encode(CachedResponse response) {
        String type = response.contentType() == null ? "" : response.contentType();
        String body = Base64.getEncoder().encodeToString(
                response.body() == null ? new byte[0] : response.body());
        return response.status() + "\n" + type + "\n" + body;
    }

    static CachedResponse decode(String raw) {
        String[] parts = raw.split("\n", 3);
        if (parts.length < 3) {
            return new CachedResponse(200, "application/json", raw.getBytes(StandardCharsets.UTF_8));
        }
        int status = Integer.parseInt(parts[0]);
        String contentType = parts[1].isBlank() ? "application/json" : parts[1];
        byte[] body = Base64.getDecoder().decode(parts[2]);
        return new CachedResponse(status, contentType, body);
    }

    public record CachedResponse(int status, String contentType, byte[] body) {
    }

    private record LocalEntry(CachedResponse payload, Instant expiresAt) {
    }
}
