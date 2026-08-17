package com.invsys.core.security;

import tools.jackson.databind.ObjectMapper;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.domain.NetworkAccessLevel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * LBAC gate for {@code X-Warehouse-Id} plus conditional-access network fencing.
 * <ul>
 *   <li>OWNER / ADMIN — skip warehouse membership validation</li>
 *   <li>PICKER / WAREHOUSE_MANAGER (and other localized roles) — header must match
 *       JWT {@code warehouse_ids} populated from {@code user_warehouses}</li>
 *   <li>Network fence — highest {@link NetworkAccessLevel} across assigned roles vs
 *       tenant CIDR allowlist and {@code mfa_verified} JWT claim</li>
 * </ul>
 * RFC 7807 problem+json on boundary violations.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class WarehouseAccessFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Warehouse-Id";

    private static final Set<String> ELEVATED = Set.of("ROLE_OWNER", "ROLE_ADMIN");

    private final ObjectMapper objectMapper;
    private final NetworkAccessPolicy networkAccessPolicy;
    private final ClientIpResolver clientIpResolver;

    public WarehouseAccessFilter(ObjectMapper objectMapper,
                                 NetworkAccessPolicy networkAccessPolicy,
                                 ClientIpResolver clientIpResolver) {
        this.objectMapper = objectMapper;
        this.networkAccessPolicy = networkAccessPolicy;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !enforceNetworkFence(request, response, auth)) {
            return;
        }

        String raw = request.getHeader(HEADER);
        List<UUID> authorized = TenantContext.getAuthorizedWarehouseIds();
        boolean elevated = isElevated(auth);

        if (raw == null || raw.isBlank()) {
            if (auth != null && auth.isAuthenticated() && !elevated && authorized.size() == 1) {
                UUID warehouseId = authorized.getFirst();
                TenantContext.setWarehouseId(warehouseId);
                MDC.put("warehouseId", warehouseId.toString());
            }
            chain.doFilter(request, response);
            return;
        }

        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        UUID warehouseId;
        try {
            warehouseId = UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "WAREHOUSE_FORBIDDEN", "Invalid X-Warehouse-Id");
            return;
        }

        // OWNER / ADMIN: skip user_warehouses membership check
        if (elevated) {
            TenantContext.setWarehouseId(warehouseId);
            MDC.put("warehouseId", warehouseId.toString());
            chain.doFilter(request, response);
            return;
        }

        // PICKER / WAREHOUSE_MANAGER / other floor roles: must be in user_warehouses-backed claims
        Set<UUID> allowed = authorized.stream().collect(Collectors.toSet());
        if (allowed.isEmpty() || !allowed.contains(warehouseId)) {
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "WAREHOUSE_FORBIDDEN",
                    "Warehouse context not authorized for this user");
            return;
        }

        TenantContext.setWarehouseId(warehouseId);
        MDC.put("warehouseId", warehouseId.toString());
        chain.doFilter(request, response);
    }

    /**
     * @return {@code false} when the response was already committed with a deny/MFA challenge
     */
    boolean enforceNetworkFence(HttpServletRequest request, HttpServletResponse response, Authentication auth)
            throws IOException {
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        if (tenantId == null) {
            return true;
        }
        List<String> roleCodes = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(code -> code != null && code.startsWith("ROLE_"))
                .map(code -> code.substring("ROLE_".length()))
                .toList();
        NetworkAccessLevel level = networkAccessPolicy.highestForRoleCodes(tenantId, roleCodes);
        List<String> cidrs = networkAccessPolicy.allowedCidrBlocks(tenantId);
        String clientIp = resolveClientIp(request);
        boolean mfaVerified = Boolean.TRUE.equals(request.getAttribute(JwtAuthFilter.ATTR_MFA_VERIFIED));
        NetworkAccessPolicy.Decision decision = networkAccessPolicy.evaluate(clientIp, cidrs, level, mfaVerified);
        if (decision == NetworkAccessPolicy.Decision.ALLOW) {
            return true;
        }
        if (decision == NetworkAccessPolicy.Decision.DENY_STRICT) {
            writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED",
                    NetworkAccessPolicy.STRICT_DENIED_DETAIL);
            return false;
        }
        writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, NetworkAccessPolicy.MFA_REQUIRED_CODE,
                NetworkAccessPolicy.MFA_REQUIRED_CODE);
        return false;
    }

    String resolveClientIp(HttpServletRequest request) {
        String resolved = clientIpResolver.resolve(request);
        if (resolved != null && !"unknown".equalsIgnoreCase(resolved)) {
            return resolved;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return resolved;
        }
        String first = forwarded.split(",")[0].trim();
        return first.isEmpty() ? resolved : first;
    }

    private static boolean isElevated(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ELEVATED::contains);
    }

    private void writeProblem(HttpServletResponse response, int status, String title, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("detail", detail);
        body.put("status", status);
        body.put("code", title);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/signup")
                || path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/warehouse/login")
                || path.startsWith("/api/v1/auth/refresh")
                || path.startsWith("/api/v1/auth/magic-login")
                || path.startsWith("/api/v1/auth/discovery")
                || path.startsWith("/api/v1/auth/sso-discover")
                || path.startsWith("/api/v1/invitations/accept")
                || path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/actuator/health");
    }
}
