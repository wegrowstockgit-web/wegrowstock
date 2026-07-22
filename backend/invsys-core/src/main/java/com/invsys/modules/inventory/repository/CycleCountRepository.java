package com.invsys.modules.inventory.repository;

import com.invsys.modules.inventory.domain.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    List<CycleCount> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    boolean existsByTenantIdAndLocationIdAndStatus(UUID tenantId, UUID locationId, String status);

    java.util.Optional<CycleCount> findByIdAndTenantId(UUID id, UUID tenantId);
}
