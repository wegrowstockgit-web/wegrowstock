package com.invsys.repository;

import com.invsys.domain.InternalRequisition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InternalRequisitionRepository extends JpaRepository<InternalRequisition, UUID> {
    List<InternalRequisition> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<InternalRequisition> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    Optional<InternalRequisition> findByTenantIdAndId(UUID tenantId, UUID id);
}
