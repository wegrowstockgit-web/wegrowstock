package com.invsys.core.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Blocks Data Plane API access when the bound tenant is {@code SUSPENDED}.
 * Runs after {@link com.invsys.core.security.JwtAuthFilter} so {@link TenantContext} is populated.
 * Complements {@link TenantIsolationFilter} (outermost ThreadLocal guard) for SOC2 dunning enforcement.
 */
@Component
public class SuspendedTenantAccessFilter extends OncePerRequestFilter {

    private final BootstrapJdbc bootstrapJdbc;

    public SuspendedTenantAccessFilter(BootstrapJdbc bootstrapJdbc) {
        this.bootstrapJdbc = bootstrapJdbc;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID tenantId = TenantContext.getTenantId().orElse(null);
        if (tenantId != null && bootstrapJdbc.isTenantSuspended(tenantId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"TENANT_SUSPENDED\",\"status\":403,"
                            + "\"detail\":\"Tenant access is suspended by the control plane\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
