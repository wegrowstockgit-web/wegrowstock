package com.invsys.modules.catalog.repository;

import com.invsys.modules.catalog.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    List<Location> findByTenantIdOrderByPathAsc(UUID tenantId);
    Optional<Location> findByTenantIdAndCode(UUID tenantId, String code);
    Optional<Location> findByTenantIdAndPath(UUID tenantId, String path);
    List<Location> findByTenantIdAndType(UUID tenantId, String type);
}
