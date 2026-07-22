package com.invsys.repository;

import com.invsys.domain.TaxRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {
    List<TaxRate> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<TaxRate> findByTenantIdAndDefaultRateTrue(UUID tenantId);

    Optional<TaxRate> findByTenantIdAndId(UUID tenantId, UUID id);
}
