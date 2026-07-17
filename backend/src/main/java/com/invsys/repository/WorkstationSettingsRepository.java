package com.invsys.repository;

import com.invsys.domain.WorkstationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkstationSettingsRepository extends JpaRepository<WorkstationSettings, UUID> {
    Optional<WorkstationSettings> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
