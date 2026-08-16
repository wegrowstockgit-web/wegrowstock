package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.security.ImpersonationHandoffStore;
import com.invsys.core.security.JwtService;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class AdminImpersonationService {

    private static final String DEFAULT_LOGIN_BASE = "https://app.invsys.com/login";

    private final BootstrapJdbc bootstrapJdbc;
    private final JwtService jwtService;
    private final ImpersonationHandoffStore impersonationHandoffStore;
    private final String loginBaseUrl;

    public AdminImpersonationService(BootstrapJdbc bootstrapJdbc,
                                     JwtService jwtService,
                                     ImpersonationHandoffStore impersonationHandoffStore,
                                     @Value("${invsys.wms-app-url:https://app.invsys.com}") String wmsAppUrl) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.jwtService = jwtService;
        this.impersonationHandoffStore = impersonationHandoffStore;
        String trimmed = wmsAppUrl == null || wmsAppUrl.isBlank() ? DEFAULT_LOGIN_BASE : wmsAppUrl.trim();
        this.loginBaseUrl = trimmed.endsWith("/login") ? trimmed : trimmed.replaceAll("/$", "") + "/login";
    }

    public ImpersonationResponse impersonate(UUID tenantId) {
        BootstrapJdbc.TenantNameSlugStatusRow tenant = bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));
        if ("SUSPENDED".equalsIgnoreCase(tenant.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_SUSPENDED",
                    "Cannot impersonate a suspended tenant");
        }

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
        String jti;
        try {
            jti = jwtService.validateAndParse(accessToken).getJWTID();
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMPERSONATION_FAILED",
                    "Failed to mint impersonation token");
        }
        if (jti == null || jti.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMPERSONATION_FAILED",
                    "Impersonation token missing jti");
        }

        String handoffCode = UUID.randomUUID() + UUID.randomUUID().toString();
        impersonationHandoffStore.register(
                jti,
                handoffCode,
                accessToken,
                Duration.ofSeconds(JwtService.IMPERSONATION_TTL_SECONDS));

        String loginUrl = loginBaseUrl + "?impersonateCode=" + handoffCode;

        return new ImpersonationResponse(
                accessToken,
                handoffCode,
                JwtService.IMPERSONATION_TTL_SECONDS,
                loginUrl,
                user.email());
    }

    public record ImpersonationResponse(
            String accessToken,
            String handoffCode,
            long expiresInSeconds,
            String loginUrl,
            String email
    ) {
    }
}
