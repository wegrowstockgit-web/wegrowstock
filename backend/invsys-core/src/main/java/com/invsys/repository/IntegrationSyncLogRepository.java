package com.invsys.repository;

import com.invsys.domain.IntegrationSyncLog;
import com.invsys.integration.channel.SyncDirection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationSyncLogRepository extends JpaRepository<IntegrationSyncLog, UUID> {

    List<IntegrationSyncLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<IntegrationSyncLog> findByTenantIdAndSystemOrderByCreatedAtDesc(UUID tenantId, String system);

    List<IntegrationSyncLog> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    List<IntegrationSyncLog> findByTenantIdAndSystemAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String system, String status);

    List<IntegrationSyncLog> findByTenantIdAndChannelIdOrderByProcessedAtDescCreatedAtDesc(
            UUID tenantId, UUID channelId);

    List<IntegrationSyncLog> findByTenantIdAndDirectionAndStatusOrderByCreatedAtDesc(
            UUID tenantId, SyncDirection direction, String status);

    Optional<IntegrationSyncLog> findByTenantIdAndChannelIdAndExternalId(
            UUID tenantId, UUID channelId, String externalId);

    Optional<IntegrationSyncLog> findFirstByTenantIdAndSystemOrderByCreatedAtDesc(UUID tenantId, String system);

    long countByTenantIdAndSystemAndStatus(UUID tenantId, String system, String status);
}
