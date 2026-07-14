package com.invsys.repository;

import com.invsys.domain.PlatformAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformAlertRepository extends JpaRepository<PlatformAlert, UUID> {
    List<PlatformAlert> findByTenantIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    List<PlatformAlert> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<PlatformAlert> findByTenantIdAndAlertTypeAndSourceSystemAndAcknowledgedAtIsNull(
            UUID tenantId, String alertType, String sourceSystem);
}
