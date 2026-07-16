package com.invsys.config;

import com.invsys.common.ApiException;
import com.invsys.ratelimit.DistributedRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Redis-backed (or local fallback) rate limit for public auth and webhook endpoints.
 * Returns RFC 7807 problem+json on 429.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final DistributedRateLimiter distributedRateLimiter;
    private final int authLimit;
    private final int terminalPinLimit;
    private final int webhookLimit;

    public RateLimitFilter(
            DistributedRateLimiter distributedRateLimiter,
            @Value("${invsys.rate-limit.auth-per-minute:60}") int authLimit,
            @Value("${invsys.rate-limit.terminal-pin-per-minute:20}") int terminalPinLimit,
            @Value("${invsys.rate-limit.webhook-per-minute:120}") int webhookLimit) {
        this.distributedRateLimiter = distributedRateLimiter;
        this.authLimit = authLimit;
        this.terminalPinLimit = terminalPinLimit;
        this.webhookLimit = webhookLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Integer limit = limitFor(path);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = "rate:ip:" + pathBucket(path) + ":" + clientKey(request);
        try {
            distributedRateLimiter.tryAcquire(key, limit, 1, WINDOW);
        } catch (ApiException ex) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType("application/problem+json");
            String detail = ex.getMessage() == null ? "Too many requests" : ex.getMessage().replace("\"", "'");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"RATE_LIMITED\",\"status\":429,\"detail\":\""
                            + detail + "\",\"retryAfterSeconds\":60}");
            return;
        }
        chain.doFilter(request, response);
    }

    private Integer limitFor(String path) {
        if (path.startsWith("/api/v1/auth/terminal-switch")
                || path.startsWith("/api/v1/auth/terminal-biometric")
                || path.startsWith("/api/v1/auth/warehouse/login")) {
            return terminalPinLimit;
        }
        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/sso-discover")
                || path.startsWith("/api/v1/invitations/accept")) {
            return authLimit;
        }
        if (path.startsWith("/api/v1/webhooks/") || path.startsWith("/api/v1/public/webhooks/")) {
            return webhookLimit;
        }
        return null;
    }

    private static String pathBucket(String path) {
        if (path.startsWith("/api/v1/auth/terminal-switch")
                || path.startsWith("/api/v1/auth/terminal-biometric")
                || path.startsWith("/api/v1/auth/warehouse/login")) {
            return "terminal-pin";
        }
        if (path.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (path.startsWith("/api/v1/webhooks/") || path.startsWith("/api/v1/public/webhooks/")) {
            return "webhook";
        }
        return "other";
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
