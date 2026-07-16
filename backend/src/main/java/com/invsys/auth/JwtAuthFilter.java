package com.invsys.auth;

import com.invsys.repository.CustomerUserMappingRepository;
import com.invsys.repository.SupplierUserMappingRepository;
import com.invsys.tenancy.TenantContext;
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

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthCookieService authCookieService;
    private final CustomerUserMappingRepository customerUserMappingRepository;
    private final SupplierUserMappingRepository supplierUserMappingRepository;

    public JwtAuthFilter(JwtService jwtService,
                         AuthCookieService authCookieService,
                         CustomerUserMappingRepository customerUserMappingRepository,
                         SupplierUserMappingRepository supplierUserMappingRepository) {
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
        this.customerUserMappingRepository = customerUserMappingRepository;
        this.supplierUserMappingRepository = supplierUserMappingRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = resolveAccessToken(request);
            if (token != null) {
                try {
                    JWTClaimsSet claims = jwtService.validateAndParse(token);
                    UUID userId = UUID.fromString(claims.getSubject());
                    UUID tenantId = UUID.fromString((String) claims.getClaim("tenant_id"));
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) claims.getClaim("roles");
                    if (roles == null) {
                        roles = List.of();
                    }

                    List<UUID> warehouseIds = parseWarehouseIds(claims.getClaim("warehouse_ids"));

                    TenantContext.setTenantId(tenantId);
                    TenantContext.setUserId(userId);
                    TenantContext.setAuthorizedWarehouseIds(warehouseIds);
                    MDC.put(com.invsys.common.MdcSupport.TENANT_ID, tenantId.toString());
                    MDC.put(com.invsys.common.MdcSupport.USER_ID, userId.toString());
                    MDC.put("tenantId", tenantId.toString());
                    MDC.put("userId", userId.toString());

                    if (roles.contains("B2B_CUSTOMER")) {
                        customerUserMappingRepository.findByUserId(userId)
                                .ifPresent(m -> TenantContext.setCustomerId(m.getCustomerId()));
                    }
                    if (roles.contains("SUPPLIER")) {
                        supplierUserMappingRepository.findByUserId(userId)
                                .ifPresent(m -> TenantContext.setSupplierId(m.getSupplierId()));
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
            chain.doFilter(request, response);
        } finally {
            // Virtual threads may reuse carrier frames — never leave tenant/MDC residue.
            TenantContext.clear();
            MDC.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Prefer HttpOnly cookie; Bearer remains for server-side/integration callers that
     * obtained a token via {@link AuthService} (never from JSON responses).
     */
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

    @SuppressWarnings("unchecked")
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
        return ids;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/signup")
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
