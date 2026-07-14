package com.invsys.repository;

import com.invsys.domain.ManufacturingOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ManufacturingOperationRepository extends JpaRepository<ManufacturingOperation, UUID> {
    List<ManufacturingOperation> findByTenantIdOrderByNameAsc(UUID tenantId);
}
