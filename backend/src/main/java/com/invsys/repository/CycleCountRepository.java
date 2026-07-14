package com.invsys.repository;

import com.invsys.domain.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {
    List<CycleCount> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    boolean existsByTenantIdAndLocationIdAndStatus(UUID tenantId, UUID locationId, String status);
}
