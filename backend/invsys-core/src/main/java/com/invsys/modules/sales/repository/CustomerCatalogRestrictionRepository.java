package com.invsys.modules.sales.repository;

import com.invsys.domain.CustomerCatalogRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerCatalogRestrictionRepository extends JpaRepository<CustomerCatalogRestriction, UUID> {
    List<CustomerCatalogRestriction> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
