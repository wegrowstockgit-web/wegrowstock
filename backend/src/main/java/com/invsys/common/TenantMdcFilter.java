package com.invsys.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Clears tenant/user MDC keys after the request. Population happens in JwtAuthFilter.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcSupport.TENANT_ID);
            MDC.remove(MdcSupport.USER_ID);
        }
    }
}
