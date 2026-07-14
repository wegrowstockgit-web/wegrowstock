package com.invsys.repository;

import com.invsys.domain.ProductionTimesheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductionTimesheetRepository extends JpaRepository<ProductionTimesheet, UUID> {
    List<ProductionTimesheet> findByTenantIdAndProductionOrderId(UUID tenantId, UUID productionOrderId);

    Optional<ProductionTimesheet> findByTenantIdAndProductionOrderIdAndUserIdAndEndTimeIsNull(
            UUID tenantId, UUID productionOrderId, UUID userId);
}
