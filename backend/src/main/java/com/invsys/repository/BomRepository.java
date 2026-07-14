package com.invsys.repository;

import com.invsys.domain.Bom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BomRepository extends JpaRepository<Bom, UUID> {
    List<Bom> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<Bom> findByTenantIdAndParentVariantId(UUID tenantId, UUID parentVariantId);
}
