package com.invsys.repository;

import com.invsys.domain.ExternalReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalReferenceRepository extends JpaRepository<ExternalReference, UUID> {

    Optional<ExternalReference> findByTenantIdAndSystemAndEntityTypeAndEntityId(
            UUID tenantId, String system, String entityType, UUID entityId);

    Optional<ExternalReference> findByTenantIdAndSystemAndExternalId(
            UUID tenantId, String system, String externalId);

    List<ExternalReference> findByTenantIdAndSystemAndEntityType(
            UUID tenantId, String system, String entityType);
}
