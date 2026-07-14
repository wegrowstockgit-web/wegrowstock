package com.invsys.repository;

import com.invsys.domain.PickingWave;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PickingWaveRepository extends JpaRepository<PickingWave, UUID> {
    List<PickingWave> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
