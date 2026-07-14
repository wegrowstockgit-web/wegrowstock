package com.invsys.repository;

import com.invsys.domain.BomOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BomOperationRepository extends JpaRepository<BomOperation, UUID> {
    List<BomOperation> findByTenantIdAndBomId(UUID tenantId, UUID bomId);
}
