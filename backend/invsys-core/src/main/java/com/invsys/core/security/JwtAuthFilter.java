package com.invsys.core.security;

import com.invsys.core.tenancy.TenantContext;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.invsys.core.common.MdcSupport;

/**
 * Resolves the authenticated principal and binds {@link TenantContext}.
 * Downstream execution is always wrapped so ThreadLocals cannot leak into the pool:
 * <pre>
 * try {
 *   TenantContext.setTenantId(...);
 *   TenantContext.setUserId(...);
 *   filterChain.doFilter(request, response);
 * } finally {
 *   // cleared only when the request is fully done (not when SSE/async starts)
 *   TenantContext.clear();
 * }
 * </pre>
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthCookieService authCookieService;
    private final PortalIdentityResolver portalIdentityResolver;

    public JwtAuthFilter(JwtService jwtService,
                         AuthCookieService authCookieService,
                         PortalIdentityResolver portalIdentityResolver) {
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
        this.portalIdentityResolver = portalIdentityResolver;
    }

    /**
     * Re-bind JWT tenant/auth on async dispatches (SSE timeout/error). The default
     * OncePerRequestFilter skip left those dispatches unauthenticated after the
     * initial kickoff cleared ThreadLocals too early.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // Resolve tenant from cookies/Bearer and bind context (no-op when unauthenticated).
            bindTenantFromTokenIfPresent(request);
            filterChain.doFilter(request, response);
        } finally {
            // Always drop tenant/MDC ThreadLocals on this thread. For SSE kickoff the
            // filter chain returns while the response stays open — do NOT clear
            // SecurityContextHolder here. Clearing it before SecurityContextHolderFilter
            // saved the context left async timeout dispatches unauthenticated
            // (AuthorizationDeniedException + AsyncRequestTimeoutException ERROR spam).
            // Async dispatches re-enter this filter (shouldNotFilterAsyncDispatch=false)
            // and re-bind tenant from the JWT/cookie.
            TenantContext.clear();
            MDC.clear();
        }
    }

    private void bindTenantFromTokenIfPresent(HttpServletRequest request) {
        String token = resolveAccessToken(request);
        if (token == null) {
            return;
        }
        try {
            // RS256 + exp/iat + TERMINAL_SWITCH tenant bind enforced inside JwtService.
            JWTClaimsSet claims = jwtService.validateAndParse(token);
            UUID userId = UUID.fromString(claims.getSubject());
            UUID tenantId = UUID.fromString((String) claims.getClaim(JwtService.CLAIM_TENANT_ID));
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.getClaim("roles");
            if (roles == null) {
                roles = List.of();
            }

            List<UUID> warehouseIds = parseWarehouseIds(claims.getClaim("warehouse_ids"));

            TenantContext.setTenantId(tenantId);
            TenantContext.setUserId(userId);
            TenantContext.setAuthorizedWarehouseIds(warehouseIds);
            MDC.put(com.invsys.core.common.MdcSupport.TENANT_ID, tenantId.toString());
            MDC.put(com.invsys.core.common.MdcSupport.USER_ID, userId.toString());
            MDC.put("tenantId", tenantId.toString());
            MDC.put("userId", userId.toString());

            if (roles.contains("B2B_CUSTOMER")) {
                portalIdentityResolver.findCustomerIdForUser(userId)
                        .ifPresent(TenantContext::setCustomerId);
            }
            if (roles.contains("SUPPLIER")) {
                portalIdentityResolver.findSupplierIdForUser(userId)
                        .ifPresent(TenantContext::setSupplierId);
            }

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .collect(Collectors.toList());
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception ignored) {
            // Stale/invalid cookie or Bearer must not block public auth routes
            // (e.g. POST /login while an expired invsys_access cookie is still present).
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String cookieToken = authCookieService.readAccessToken(request);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private static List<UUID> parseWarehouseIds(Object claim) {
        if (!(claim instanceof List<?> raw) || raw.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>();
        for (Object item : raw) {
            if (item != null) {
                ids.add(UUID.fromString(item.toString()));
            }
        }
        return List.copyOf(ids);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/control-plane/")
                || path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/warehouse/login")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/magic-login")
                || path.startsWith("/api/v1/auth/sso-discover")
                || path.startsWith("/api/v1/invitations/accept")
                || path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/saml2/")
                || path.equals("/.well-known/jwks.json")
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs");
    }
}
