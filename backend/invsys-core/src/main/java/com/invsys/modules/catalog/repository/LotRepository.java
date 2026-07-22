package com.invsys.modules.catalog.repository;

import com.invsys.modules.catalog.domain.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LotRepository extends JpaRepository<Lot, UUID> {
    Optional<Lot> findByTenantIdAndVariantIdAndLotNumber(UUID tenantId, UUID variantId, String lotNumber);

    Optional<Lot> findFirstByTenantIdAndLotNumber(UUID tenantId, String lotNumber);
}
