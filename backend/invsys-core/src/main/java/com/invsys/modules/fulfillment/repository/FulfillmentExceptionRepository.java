package com.invsys.modules.fulfillment.repository;

import com.invsys.modules.fulfillment.domain.FulfillmentException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FulfillmentExceptionRepository extends JpaRepository<FulfillmentException, UUID> {
    List<FulfillmentException> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<FulfillmentException> findByTenantIdAndResolutionStatusOrderByCreatedAtDesc(
            UUID tenantId, String resolutionStatus);

    Optional<FulfillmentException> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<FulfillmentException> findFirstByTenantIdAndAllocationIdAndResolutionStatus(
            UUID tenantId, UUID allocationId, String resolutionStatus);
}
