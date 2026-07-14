package com.invsys.repository;

import com.invsys.domain.PickingBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PickingBatchRepository extends JpaRepository<PickingBatch, UUID> {
    List<PickingBatch> findByWaveId(UUID waveId);

    Optional<PickingBatch> findFirstByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
}
