package com.invsys.repository;

import com.invsys.domain.ThermalPrinter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThermalPrinterRepository extends JpaRepository<ThermalPrinter, UUID> {

    List<ThermalPrinter> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<ThermalPrinter> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ThermalPrinter> findByTenantIdAndIsDefaultTrue(UUID tenantId);
}
