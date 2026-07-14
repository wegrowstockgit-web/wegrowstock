package com.invsys.repository;

import com.invsys.domain.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CostCenterRepository extends JpaRepository<CostCenter, UUID> {
    List<CostCenter> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<CostCenter> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<CostCenter> findByTenantIdAndCode(UUID tenantId, String code);
}
