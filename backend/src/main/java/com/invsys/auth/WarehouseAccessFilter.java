package com.invsys.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invsys.tenancy.TenantContext;
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
import java.util.UUID;

/**
 * Validates optional {@code X-Warehouse-Id} against JWT warehouse_ids (LBAC).
 * OWNER/ADMIN may select any warehouse present in their authorized claim list
 * (populated with all tenant warehouses at login). Localized roles may only use mapped facilities.
 * When a localized user has exactly one authorized warehouse and no header is sent,
 * that warehouse is applied automatically (terminal lockdown).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class WarehouseAccessFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Warehouse-Id";

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
        boolean elevated = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> "ROLE_OWNER".equals(a) || "ROLE_ADMIN".equals(a));

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

        if (!elevated && (authorized.isEmpty() || !authorized.contains(warehouseId))) {
            writeForbidden(response, "Warehouse context not authorized for this user");
            return;
        }
        if (elevated && !authorized.isEmpty() && !authorized.contains(warehouseId)) {
            writeForbidden(response, "Warehouse does not belong to this tenant context");
            return;
        }

        TenantContext.setWarehouseId(warehouseId);
        MDC.put("warehouseId", warehouseId.toString());
        chain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
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
