package com.invsys.repository;

import com.invsys.domain.LicensePlate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicensePlateRepository extends JpaRepository<LicensePlate, UUID> {
    Optional<LicensePlate> findByTenantIdAndLpnBarcode(UUID tenantId, String lpnBarcode);

    Optional<LicensePlate> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LicensePlate> findByTenantIdAndStatus(UUID tenantId, String status);
}
