package com.invsys.repository;

import com.invsys.domain.WaveReplenishmentTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaveReplenishmentTriggerRepository extends JpaRepository<WaveReplenishmentTrigger, UUID> {

    List<WaveReplenishmentTrigger> findByTenantIdAndStatus(UUID tenantId, String status);

    Optional<WaveReplenishmentTrigger> findByTenantIdAndVariantIdAndLocationIdAndStatusIn(
            UUID tenantId, UUID variantId, UUID locationId, List<String> statuses);
}
