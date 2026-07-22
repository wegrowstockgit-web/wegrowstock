package com.invsys.repository;

import com.invsys.domain.IntegrationChannel;
import com.invsys.integration.channel.IntegrationChannelStatus;
import com.invsys.integration.channel.IntegrationChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationChannelRepository extends JpaRepository<IntegrationChannel, UUID> {

    List<IntegrationChannel> findByTenantIdOrderByChannelTypeAsc(UUID tenantId);

    Optional<IntegrationChannel> findByTenantIdAndChannelType(UUID tenantId, IntegrationChannelType channelType);

    Optional<IntegrationChannel> findByTenantIdAndChannelTypeAndStatus(
            UUID tenantId, IntegrationChannelType channelType, IntegrationChannelStatus status);

    List<IntegrationChannel> findByTenantIdAndStatus(UUID tenantId, IntegrationChannelStatus status);

    /** Active connection settings for webhook / inbound ingestion hot-path. */
    default Optional<IntegrationChannel> findActiveByTenantAndType(UUID tenantId, IntegrationChannelType channelType) {
        return findByTenantIdAndChannelTypeAndStatus(tenantId, channelType, IntegrationChannelStatus.ACTIVE);
    }
}
