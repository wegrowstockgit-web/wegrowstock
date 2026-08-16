package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Resolves the tenant for unauthenticated showroom endpoints (apply + guest catalog).
 * Uses the bootstrap (owner) connection so slug lookup is not blocked by RLS.
 */
@Component
public class PublicShowroomTenantResolver {

    private final BootstrapJdbc bootstrapJdbc;

    public PublicShowroomTenantResolver(BootstrapJdbc bootstrapJdbc) {
        this.bootstrapJdbc = bootstrapJdbc;
    }

    public UUID resolve(String headerSlug, String bodyOrQuerySlug) {
        String slug = firstNonBlank(headerSlug, bodyOrQuerySlug);
        if (slug != null) {
            return bootstrapJdbc.findTenantIdBySlug(slug.trim())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_TENANT",
                            "Unknown wholesale tenant"));
        }
        List<UUID> active = bootstrapJdbc.listActiveTenantIds();
        if (active.size() == 1) {
            return active.getFirst();
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "TENANT_REQUIRED",
                "Specify X-Tenant-Slug for this wholesale showroom");
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
