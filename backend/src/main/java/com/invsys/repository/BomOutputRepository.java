package com.invsys.repository;

import com.invsys.domain.BomOutput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BomOutputRepository extends JpaRepository<BomOutput, UUID> {
    List<BomOutput> findByTenantIdAndBomIdOrderByOutputTypeAsc(UUID tenantId, UUID bomId);
}
