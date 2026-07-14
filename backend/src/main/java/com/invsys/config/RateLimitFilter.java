package com.invsys.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory rate limit for public auth and webhook endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT = 60;
    private static final int WEBHOOK_LIMIT = 120;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Integer limit = limitFor(path);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = limit + ":" + clientKey(request) + ":" + pathBucket(path);
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAtMs >= WINDOW_MS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (window.count.get() > limit) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static Integer limitFor(String path) {
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/invitations/accept")) {
            return AUTH_LIMIT;
        }
        if (path.startsWith("/api/v1/webhooks/") || path.startsWith("/api/v1/public/webhooks/")) {
            return WEBHOOK_LIMIT;
        }
        return null;
    }

    private static String pathBucket(String path) {
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (path.startsWith("/api/v1/webhooks/") || path.startsWith("/api/v1/public/webhooks/")) {
            return "webhooks";
        }
        return path;
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private record Window(long startedAtMs, AtomicInteger count) {
    }
}
