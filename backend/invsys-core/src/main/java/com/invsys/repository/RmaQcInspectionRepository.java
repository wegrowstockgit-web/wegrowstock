package com.invsys.repository;

import com.invsys.domain.RmaQcInspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RmaQcInspectionRepository extends JpaRepository<RmaQcInspection, UUID> {
    List<RmaQcInspection> findByTenantIdAndReturnLineIdOrderByCreatedAtDesc(UUID tenantId, UUID returnLineId);
}
