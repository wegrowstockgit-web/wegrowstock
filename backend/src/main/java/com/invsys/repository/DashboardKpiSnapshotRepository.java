package com.invsys.repository;

import com.invsys.domain.DashboardKpiSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DashboardKpiSnapshotRepository extends JpaRepository<DashboardKpiSnapshot, UUID> {
    Optional<DashboardKpiSnapshot> findByTenantId(UUID tenantId);
}
