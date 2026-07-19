package com.invsys.idempotency;

import com.invsys.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Administrative idempotency gate for mutating warehouse APIs.
 * Reads {@code Idempotency-Key}; on Redis/local cache hit returns the cached 2xx body
 * and skips controller execution.
 */
@Component
public class RedisIdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "X-Idempotency-Replayed";

    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RedisIdempotencyStore store;

    public RedisIdempotencyFilter(RedisIdempotencyStore store) {
        this.store = store;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null || !METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/v1/")) {
            return true;
        }
        if (path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/api/v1/public/")) {
            return true;
        }
        String key = request.getHeader(HEADER);
        return key == null || key.isBlank();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<UUID> tenantId = TenantContext.getTenantId();
        if (tenantId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(HEADER).trim();
        Optional<RedisIdempotencyStore.CachedResponse> cached = store.get(tenantId.get(), key);
        if (cached.isPresent()) {
            RedisIdempotencyStore.CachedResponse hit = cached.get();
            response.setStatus(hit.status());
            if (hit.contentType() != null && !hit.contentType().isBlank()) {
                response.setContentType(hit.contentType());
            } else {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            }
            response.setHeader(REPLAYED_HEADER, "true");
            byte[] body = hit.body() != null ? hit.body() : new byte[0];
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrapped);
        int status = wrapped.getStatus();
        if (status >= 200 && status < 300) {
            byte[] body = wrapped.getContentAsByteArray();
            store.put(tenantId.get(), key, new RedisIdempotencyStore.CachedResponse(
                    status,
                    wrapped.getContentType(),
                    body));
        }
        wrapped.copyBodyToResponse();
    }
}
