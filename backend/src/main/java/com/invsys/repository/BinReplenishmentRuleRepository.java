package com.invsys.repository;

import com.invsys.domain.BinReplenishmentRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BinReplenishmentRuleRepository extends JpaRepository<BinReplenishmentRule, UUID> {

    List<BinReplenishmentRule> findByTenantId(UUID tenantId);

    Optional<BinReplenishmentRule> findByTenantIdAndLocationIdAndVariantId(
            UUID tenantId, UUID locationId, UUID variantId);
}
