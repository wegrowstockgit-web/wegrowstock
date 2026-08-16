package com.invsys.modules.inventory.api;

import com.invsys.modules.inventory.domain.Allocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllocationLookup {

    Optional<Allocation> findById(UUID id);

    Optional<Allocation> findByTenantIdAndId(UUID tenantId, UUID id);

    List<Allocation> findBySalesOrderLineIdAndStatus(UUID salesOrderLineId, String status);

    List<Allocation> findByTenantIdAndVariantIdAndStatus(UUID tenantId, UUID variantId, String status);

    Allocation save(Allocation allocation);

    Allocation saveAndFlush(Allocation allocation);
}
