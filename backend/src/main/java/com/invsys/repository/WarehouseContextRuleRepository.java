package com.invsys.repository;

import com.invsys.domain.WarehouseContextRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarehouseContextRuleRepository extends JpaRepository<WarehouseContextRule, UUID> {
    List<WarehouseContextRule> findByTenantIdAndEnabledTrueOrderByPriorityAsc(UUID tenantId);

    List<WarehouseContextRule> findByTenantIdOrderByPriorityAsc(UUID tenantId);
}
