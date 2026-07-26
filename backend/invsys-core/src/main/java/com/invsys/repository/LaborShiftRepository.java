package com.invsys.repository;

import com.invsys.domain.LaborShift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LaborShiftRepository extends JpaRepository<LaborShift, UUID> {

    Optional<LaborShift> findByTenantIdAndUserIdAndStatus(UUID tenantId, UUID userId, String status);

    List<LaborShift> findByTenantIdAndUserIdOrderByClockInDesc(UUID tenantId, UUID userId);
}
