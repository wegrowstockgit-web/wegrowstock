package com.invsys.repository;

import com.invsys.domain.IntegrationSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, UUID> {
    List<IntegrationSyncLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<IntegrationSyncLog> findByTenantIdAndSystemOrderByCreatedAtDesc(UUID tenantId, String system);

    List<IntegrationSyncLog> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<IntegrationSyncLog> findByTenantIdAndSystemAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String system, String status);
}
