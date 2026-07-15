package com.invsys.repository;

import com.invsys.domain.TaxScheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxSchemeRepository extends JpaRepository<TaxScheme, UUID> {
    List<TaxScheme> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<TaxScheme> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<TaxScheme> findFirstByTenantIdAndActiveTrueOrderByCreatedAtAsc(UUID tenantId);
}
