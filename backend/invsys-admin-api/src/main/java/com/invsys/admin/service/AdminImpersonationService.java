package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.security.JwtService;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class AdminImpersonationService {

    private static final String DEFAULT_LOGIN_BASE = "https://app.invsys.com/login";

    private final BootstrapJdbc bootstrapJdbc;
    private final JwtService jwtService;

    public AdminImpersonationService(BootstrapJdbc bootstrapJdbc, JwtService jwtService) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.jwtService = jwtService;
    }

    public ImpersonationResponse impersonate(UUID tenantId) {
        bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        BootstrapJdbc.ImpersonationUserRow user = bootstrapJdbc.findImpersonationUser(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ACTIVE_USER",
                        "No active user available for impersonation"));

        List<UUID> warehouses = bootstrapJdbc.findWarehouseIdsForUser(tenantId, user.userId());
        if (warehouses.isEmpty()) {
            warehouses = bootstrapJdbc.findAllWarehouseIds(tenantId);
        }

        List<String> roles = user.roles() == null || user.roles().isEmpty()
                ? List.of("VIEWER")
                : user.roles();

        String accessToken = jwtService.generateImpersonationAccessToken(
                user.userId(), tenantId, roles, warehouses);

        String loginUrl = DEFAULT_LOGIN_BASE + "?impersonateToken="
                + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);

        return new ImpersonationResponse(
                accessToken,
                JwtService.IMPERSONATION_TTL_SECONDS,
                loginUrl,
                user.email());
    }

    public record ImpersonationResponse(
            String accessToken,
            long expiresInSeconds,
            String loginUrl,
            String email
    ) {
    }
}
