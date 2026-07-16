package com.invsys.gateway;

import com.invsys.tenancy.BootstrapJdbc;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Dynamic CORS allow-list resolved at the API gateway edge:
 * static env origins + {@code FRONTEND_URL} + {@code https://} hosts for DNS-ACTIVE tenant domains.
 */
@Service
public class DynamicCorsWhitelist {

    public static final String REDIS_KEY = "cors:gateway:allowed_origins";

    private final BootstrapJdbc bootstrapJdbc;
    private final StringRedisTemplate redis;
    private final Set<String> staticOrigins;
    private final boolean includeVerifiedDomains;
    private final Duration cacheTtl;
    private final AtomicReference<Cached> localCache = new AtomicReference<>();

    public DynamicCorsWhitelist(
            BootstrapJdbc bootstrapJdbc,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Value("${invsys.security.cors-allowed-origins}") String allowedOrigins,
            @Value("${invsys.frontend-url:}") String frontendUrl,
            @Value("${invsys.security.cors-include-verified-domains:true}") boolean includeVerifiedDomains,
            @Value("${invsys.security.cors-cache-seconds:30}") long cacheSeconds) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.redis = redisProvider.getIfAvailable();
        this.includeVerifiedDomains = includeVerifiedDomains;
        this.cacheTtl = Duration.ofSeconds(Math.max(cacheSeconds, 5));
        LinkedHashSet<String> staticSet = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::normalizeOrigin)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String normalized = normalizeOrigin(frontendUrl);
            if (!normalized.isEmpty()) {
                staticSet.add(normalized);
            }
        }
        this.staticOrigins = Set.copyOf(staticSet);
    }

    public boolean isAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        String normalized = normalizeOrigin(origin);
        return !normalized.isEmpty() && snapshot().contains(normalized);
    }

    public Set<String> snapshot() {
        long now = System.currentTimeMillis();
        Cached cached = localCache.get();
        if (cached != null && cached.expiresAtMs > now) {
            return cached.origins;
        }
        Set<String> fromRedis = readRedis();
        if (fromRedis != null) {
            localCache.set(new Cached(fromRedis, now + cacheTtl.toMillis()));
            return fromRedis;
        }
        Set<String> computed = compute();
        writeRedis(computed);
        localCache.set(new Cached(Set.copyOf(computed), now + cacheTtl.toMillis()));
        return computed;
    }

    /** Drop caches after domain verification changes. */
    public void invalidate() {
        localCache.set(null);
        if (redis != null) {
            try {
                redis.delete(REDIS_KEY);
            } catch (RuntimeException ignored) {
                // local cache already cleared
            }
        }
    }

    private Set<String> compute() {
        LinkedHashSet<String> origins = new LinkedHashSet<>(staticOrigins);
        if (!includeVerifiedDomains) {
            return Set.copyOf(origins);
        }
        try {
            for (String domain : bootstrapJdbc.listActiveVerifiedDomainNames()) {
                if (domain == null || domain.isBlank()) {
                    continue;
                }
                String host = domain.trim().toLowerCase(Locale.ROOT);
                if (host.startsWith("*.")) {
                    continue; // never allow wildcard hosts
                }
                origins.add(normalizeOrigin("https://" + host));
                if (!host.startsWith("www.")) {
                    origins.add(normalizeOrigin("https://www." + host));
                }
            }
        } catch (RuntimeException ex) {
            // Fail closed to static env origins if DB is unavailable
        }
        return Set.copyOf(origins);
    }

    private Set<String> readRedis() {
        if (redis == null) {
            return null;
        }
        try {
            String raw = redis.opsForValue().get(REDIS_KEY);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return Arrays.stream(raw.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void writeRedis(Set<String> origins) {
        if (redis == null) {
            return;
        }
        try {
            redis.opsForValue().set(REDIS_KEY, String.join("\n", origins), cacheTtl);
        } catch (RuntimeException ignored) {
            // optional cache
        }
    }

    String normalizeOrigin(String origin) {
        try {
            URI uri = URI.create(origin.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "";
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = ("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443)
                    || port == -1;
            return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException | NullPointerException ex) {
            return "";
        }
    }

    private record Cached(Set<String> origins, long expiresAtMs) {
    }
}
