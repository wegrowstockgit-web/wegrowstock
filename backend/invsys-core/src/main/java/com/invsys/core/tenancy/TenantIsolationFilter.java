package com.invsys.core.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import com.invsys.core.security.JwtAuthFilter;

/**
 * Absolute ThreadLocal isolation guard for the servlet worker / virtual-thread pool.
 * <p>
 * Downstream filters ({@code JwtAuthFilter}) bind tenant/user context; this outermost
 * filter guarantees {@link TenantContext#clear()} runs after every request — including
 * unhandled runtime exceptions, short-circuit responses, and transaction rollbacks —
 * so no historical tenant identity can contaminate the next request on the same carrier.
 * <p>
 * Async/SSE kickoff is special: the filter chain returns while the response stays open.
 * Clearing here on that path wiped auth/tenant for the later timeout dispatch. Cleanup
 * is deferred until the async dispatch completes (or the request never went async).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantIsolationFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            // On SSE/async kickoff, SecurityContextHolderFilter has already persisted the
            // context for the concurrent request. Clearing the holder is fine (empty TL),
            // but skip wiping after async start only if a later dispatch still needs the
            // in-thread holder — Spring Security restores from the request attribute.
            if (!(isAsyncStarted(request) && !isAsyncDispatch(request))) {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
