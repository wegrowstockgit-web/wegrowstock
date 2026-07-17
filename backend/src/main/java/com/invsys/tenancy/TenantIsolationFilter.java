package com.invsys.tenancy;

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

/**
 * Absolute ThreadLocal isolation guard for the servlet worker / virtual-thread pool.
 * <p>
 * Downstream filters ({@code JwtAuthFilter}) bind tenant/user context; this outermost
 * filter guarantees {@link TenantContext#clear()} runs after every request — including
 * unhandled runtime exceptions, short-circuit responses, and transaction rollbacks —
 * so no historical tenant identity can contaminate the next request on the same carrier.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantIsolationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }
}
