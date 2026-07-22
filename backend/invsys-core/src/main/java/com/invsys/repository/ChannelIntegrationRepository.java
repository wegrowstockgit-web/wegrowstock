package com.invsys.repository;

import com.invsys.domain.ChannelIntegration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelIntegrationRepository extends JpaRepository<ChannelIntegration, UUID> {
    List<ChannelIntegration> findByTenantIdOrderByPlatformAsc(UUID tenantId);

    Optional<ChannelIntegration> findByPlatformAndShopIdentifier(String platform, String shopIdentifier);
}
