package com.invsys.core.security;

import tools.jackson.databind.ObjectMapper;
import com.invsys.core.tenancy.TenantContext;
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
 * LBAC gate for {@code X-Warehouse-Id}.
 * <ul>
 *   <li>OWNER / ADMIN — skip warehouse membership validation</li>
 *   <li>PICKER / WAREHOUSE_MANAGER (and other localized roles) — header must match
 *       JWT {@code warehouse_ids} populated from {@code user_warehouses}</li>
 * </ul>
 * RFC 7807 problem+json on boundary violations.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class WarehouseAccessFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Warehouse-Id";

    private static final Set<String> ELEVATED = Set.of("ROLE_OWNER", "ROLE_ADMIN");

    private final ObjectMapper objectMapper;

    public WarehouseAccessFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
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
            writeForbidden(response, "Invalid X-Warehouse-Id");
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
            writeForbidden(response, "Warehouse context not authorized for this user");
            return;
        }

        TenantContext.setWarehouseId(warehouseId);
        MDC.put("warehouseId", warehouseId.toString());
        chain.doFilter(request, response);
    }

    private static boolean isElevated(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ELEVATED::contains);
    }

    private void writeForbidden(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "WAREHOUSE_FORBIDDEN");
        body.put("detail", detail);
        body.put("status", 403);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/webhooks/")
                || path.startsWith("/api/v1/public/")
                || path.startsWith("/actuator/health");
    }
}
