package com.invsys.integration.webhooks;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Rejects Stripe/Shopify webhook replays whose signature timestamps drift more than 300 seconds
 * from wall clock (HTTP 401).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class WebhookReplayDriftFilter extends OncePerRequestFilter {

    static final long MAX_DRIFT_SECONDS = 300L;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return !(path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/api/v1/public/webhooks/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        Long eventEpoch = extractEventEpochSeconds(path, request);
        if (eventEpoch == null) {
            // Non-Stripe/Shopify public webhooks (EasyPost, accounting) skip timestamp gating.
            if (!requiresTimestamp(path, request)) {
                chain.doFilter(request, response);
                return;
            }
            reject(response, "Missing webhook signature timestamp");
            return;
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - eventEpoch) > MAX_DRIFT_SECONDS) {
            reject(response, "Webhook timestamp outside allowed 300s clock-drift window");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean requiresTimestamp(String path, HttpServletRequest request) {
        if (path.contains("/webhooks/stripe") || path.endsWith("/stripe-platform")) {
            return true;
        }
        if (path.contains("/channels/shopify") || path.contains("/webhooks/shopify")) {
            return true;
        }
        // Stripe-Signature present on any webhook path → enforce
        return request.getHeader("Stripe-Signature") != null
                || request.getHeader("X-Shopify-Hmac-Sha256") != null
                || request.getHeader("X-Shopify-Triggered-At") != null;
    }

    private static Long extractEventEpochSeconds(String path, HttpServletRequest request) {
        String stripeSig = request.getHeader("Stripe-Signature");
        if (stripeSig != null && !stripeSig.isBlank()) {
            return parseStripeTimestamp(stripeSig);
        }
        if (path.contains("/channels/shopify")
                || path.contains("/webhooks/shopify")
                || request.getHeader("X-Shopify-Hmac-Sha256") != null) {
            return parseShopifyTimestamp(request.getHeader("X-Shopify-Triggered-At"));
        }
        return null;
    }

    static Long parseStripeTimestamp(String signatureHeader) {
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && "t".equals(kv[0])) {
                try {
                    return Long.parseLong(kv[1]);
                } catch (NumberFormatException ex) {
                    return null;
                }
            }
        }
        return null;
    }

    static Long parseShopifyTimestamp(String triggeredAt) {
        if (triggeredAt == null || triggeredAt.isBlank()) {
            return null;
        }
        String value = triggeredAt.trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            // fall through to Instant parse
        }
        try {
            return Instant.parse(value).getEpochSecond();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static void reject(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String safe = detail.replace("\"", "'");
        response.getWriter().write(
                "{\"status\":\"replay_rejected\",\"detail\":\"" + safe + "\"}");
    }
}
