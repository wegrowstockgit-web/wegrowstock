package com.invsys.repository;

import com.invsys.domain.ManufacturingWorkCenter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ManufacturingWorkCenterRepository extends JpaRepository<ManufacturingWorkCenter, UUID> {
    List<ManufacturingWorkCenter> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<ManufacturingWorkCenter> findByTenantIdAndCode(UUID tenantId, String code);
}
