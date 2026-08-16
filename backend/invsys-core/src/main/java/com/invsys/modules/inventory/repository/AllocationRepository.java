package com.invsys.modules.inventory.repository;

import com.invsys.modules.inventory.domain.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AllocationRepository extends JpaRepository<Allocation, UUID> {
    List<Allocation> findBySalesOrderLineIdAndStatus(UUID salesOrderLineId, String status);

    List<Allocation> findByProductionOrderIdAndStatus(UUID productionOrderId, String status);

    List<Allocation> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Allocation> findByTenantIdAndVariantIdAndStatus(UUID tenantId, UUID variantId, String status);

    java.util.Optional<Allocation> findByTenantIdAndId(UUID tenantId, UUID id);
}
