package com.invsys.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API-gateway edge CORS enforcement. Dynamic whitelist is the only authority for
 * credentialed cross-origin access; disallowed Origins never reach app handlers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiGatewayCorsFilter extends OncePerRequestFilter {

    private static final List<String> ALLOW_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final String ALLOW_HEADERS = String.join(", ",
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Request-Id",
            "X-Warehouse-Id",
            "Stripe-Signature",
            "X-Shopify-Hmac-Sha256",
            "X-Shopify-Shop-Domain",
            "X-Shopify-Topic",
            "X-Shopify-Webhook-Id",
            "X-Hmac-Signature");
    private static final String EXPOSE_HEADERS = "X-Request-Id, Retry-After";

    private final DynamicCorsWhitelist whitelist;

    public ApiGatewayCorsFilter(DynamicCorsWhitelist whitelist) {
        this.whitelist = whitelist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        if (!whitelist.isAllowed(origin)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"CORS_ORIGIN_DENIED\",\"status\":403,"
                            + "\"detail\":\"Origin is not on the API gateway CORS whitelist\"}");
            return;
        }

        applyCorsHeaders(response, origin);
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            response.setStatus(HttpStatus.NO_CONTENT.value());
            return;
        }
        chain.doFilter(request, response);
    }

    private static void applyCorsHeaders(HttpServletResponse response, String origin) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, String.join(", ", ALLOW_METHODS));
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, ALLOW_HEADERS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, EXPOSE_HEADERS);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "600");
        response.setHeader(HttpHeaders.VARY, "Origin");
    }
}
