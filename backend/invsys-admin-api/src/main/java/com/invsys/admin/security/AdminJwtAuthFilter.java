package com.invsys.admin.security;

import com.invsys.core.security.JwtService;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Authenticates control-plane JWTs. Does not bind {@code TenantContext} —
 * platform admins are not tenant principals.
 */
@Component
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminCookieService adminCookieService;

    public AdminJwtAuthFilter(JwtService jwtService, AdminCookieService adminCookieService) {
        this.jwtService = jwtService;
        this.adminCookieService = adminCookieService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveAccessToken(request);
        if (token != null) {
            try {
                JWTClaimsSet claims = jwtService.validateAndParse(token);
                if (isPlatformAdmin(claims)) {
                    UUID adminId = UUID.fromString(claims.getSubject());
                    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
                    var auth = new UsernamePasswordAuthenticationToken(adminId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static boolean isPlatformAdmin(JWTClaimsSet claims) {
        if (JwtService.TOKEN_TYPE_PLATFORM_ADMIN.equals(claims.getClaim(JwtService.CLAIM_TOKEN_TYPE))) {
            return true;
        }
        Object platformAdmin = claims.getClaim(JwtService.CLAIM_PLATFORM_ADMIN);
        if (Boolean.TRUE.equals(platformAdmin)) {
            return true;
        }
        Object roles = claims.getClaim("roles");
        if (roles instanceof List<?> raw) {
            return raw.stream().anyMatch("SUPER_ADMIN"::equals);
        }
        return false;
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String cookieToken = adminCookieService.readAccessToken(request);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/control-plane/auth/login")
                || path.startsWith("/api/v1/control-plane/auth/csrf")
                || path.startsWith("/actuator/health");
    }
}
