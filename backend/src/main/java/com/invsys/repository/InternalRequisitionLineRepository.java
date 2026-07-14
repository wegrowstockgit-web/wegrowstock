package com.invsys.repository;

import com.invsys.domain.InternalRequisitionLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InternalRequisitionLineRepository extends JpaRepository<InternalRequisitionLine, UUID> {
    List<InternalRequisitionLine> findByTenantIdAndRequisitionIdOrderByCreatedAtAsc(UUID tenantId, UUID requisitionId);
}
