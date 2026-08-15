package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminTenantLifecycleService {

    private static final Set<String> ALLOWED = Set.of("ACTIVE", "SUSPENDED");

    private final BootstrapJdbc bootstrapJdbc;

    public AdminTenantLifecycleService(BootstrapJdbc bootstrapJdbc) {
        this.bootstrapJdbc = bootstrapJdbc;
    }

    @Transactional
    public TenantStatusView updateStatus(UUID tenantId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
                    "status must be ACTIVE or SUSPENDED");
        }

        BootstrapJdbc.TenantNameSlugStatusRow tenant = bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        bootstrapJdbc.updateTenantStatus(tenantId, normalized);
        return new TenantStatusView(tenant.tenantId(), tenant.name(), tenant.slug(), normalized);
    }

    public record TenantStatusView(UUID tenantId, String name, String slug, String status) {
    }
}
