package com.invsys.repository;

import com.invsys.domain.TeamLaborRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TeamLaborRateRepository extends JpaRepository<TeamLaborRate, UUID> {
    Optional<TeamLaborRate> findByTenantIdAndUserId(UUID tenantId, UUID userId);
}
