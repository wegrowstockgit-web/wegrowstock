package com.invsys.repository;

import com.invsys.domain.VehicleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VehicleAssignmentRepository extends JpaRepository<VehicleAssignment, UUID> {
    Optional<VehicleAssignment> findByTenantIdAndTechnicianUserIdAndReturnedAtIsNull(UUID tenantId, UUID technicianUserId);

    Optional<VehicleAssignment> findByTenantIdAndId(UUID tenantId, UUID id);
}
