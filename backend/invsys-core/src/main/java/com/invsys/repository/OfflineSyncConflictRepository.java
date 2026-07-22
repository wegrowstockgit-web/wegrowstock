package com.invsys.repository;

import com.invsys.domain.OfflineSyncConflict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfflineSyncConflictRepository extends JpaRepository<OfflineSyncConflict, UUID> {
    List<OfflineSyncConflict> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<OfflineSyncConflict> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<OfflineSyncConflict> findByTenantIdAndId(UUID tenantId, UUID id);
}
