package com.invsys.core.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Core port for B2B portal identity binding. Feature modules supply the JPA-backed
 * implementation so {@link JwtAuthFilter} never imports sales/purchasing packages.
 */
public interface PortalIdentityResolver {

    Optional<UUID> findCustomerIdForUser(UUID userId);

    Optional<UUID> findSupplierIdForUser(UUID userId);
}
