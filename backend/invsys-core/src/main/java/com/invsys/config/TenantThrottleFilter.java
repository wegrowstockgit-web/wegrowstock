package com.invsys.config;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.ratelimit.DistributedRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * Applies control-plane kill-switch and custom RPS to authenticated tenant traffic.
 * Runs after JWT bind so {@link TenantContext} is populated.
 */
@Component
public class TenantThrottleFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofSeconds(1);

    private final DistributedRateLimiter distributedRateLimiter;

    public TenantThrottleFilter(DistributedRateLimiter distributedRateLimiter) {
        this.distributedRateLimiter = distributedRateLimiter;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        if (tenantId == null || skipPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String key = "rate:" + tenantId + ":api";
        try {
            distributedRateLimiter.tryAcquire(key, DistributedRateLimiter.DEFAULT_TENANT_RPS, 1, WINDOW);
        } catch (ApiException ex) {
            int retry = 1;
            Object retryProp = ex.getProperties().get("retryAfterSeconds");
            if (retryProp instanceof Number n) {
                retry = Math.max(1, n.intValue());
            }
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retry));
            response.setContentType("application/problem+json");
            String code = ex.getCode() == null ? "RATE_LIMITED" : ex.getCode();
            String detail = ex.getMessage() == null ? "Too many requests" : ex.getMessage().replace("\"", "'");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"" + code + "\",\"status\":429,\"detail\":\""
                            + detail + "\",\"retryAfterSeconds\":" + retry + "}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean skipPath(String path) {
        if (path == null) {
            return true;
        }
        return path.startsWith("/actuator")
                || path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/impersonation/accept")
                || path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/api/v1/public/");
    }
}
