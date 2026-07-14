package com.invsys.repository;

import com.invsys.domain.SerialNumber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SerialNumberRepository extends JpaRepository<SerialNumber, UUID> {
    Optional<SerialNumber> findByTenantIdAndSerialNumber(UUID tenantId, String serialNumber);

    Optional<SerialNumber> findByTenantIdAndVariantIdAndSerialNumber(UUID tenantId, UUID variantId, String serialNumber);
}
