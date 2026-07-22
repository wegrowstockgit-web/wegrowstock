package com.invsys.service;

import com.invsys.core.security.PortalIdentityResolver;
import com.invsys.domain.CustomerUserMapping;
import com.invsys.domain.SupplierUserMapping;
import com.invsys.modules.purchasing.repository.SupplierUserMappingRepository;
import com.invsys.modules.sales.repository.CustomerUserMappingRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Feature-backed adapter for {@link PortalIdentityResolver} (keeps core.security free of module imports).
 */
@Component
public class JpaPortalIdentityResolver implements PortalIdentityResolver {

    private final CustomerUserMappingRepository customerUserMappingRepository;
    private final SupplierUserMappingRepository supplierUserMappingRepository;

    public JpaPortalIdentityResolver(CustomerUserMappingRepository customerUserMappingRepository,
                                     SupplierUserMappingRepository supplierUserMappingRepository) {
        this.customerUserMappingRepository = customerUserMappingRepository;
        this.supplierUserMappingRepository = supplierUserMappingRepository;
    }

    @Override
    public Optional<UUID> findCustomerIdForUser(UUID userId) {
        return customerUserMappingRepository.findByUserId(userId).map(CustomerUserMapping::getCustomerId);
    }

    @Override
    public Optional<UUID> findSupplierIdForUser(UUID userId) {
        return supplierUserMappingRepository.findByUserId(userId).map(SupplierUserMapping::getSupplierId);
    }
}
