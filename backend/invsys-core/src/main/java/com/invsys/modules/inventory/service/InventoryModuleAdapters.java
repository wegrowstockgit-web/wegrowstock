package com.invsys.modules.inventory.service;

import com.invsys.modules.inventory.api.AllocationLookup;
import com.invsys.modules.inventory.api.InventoryLedgerLookup;
import com.invsys.modules.inventory.api.InventoryLevelLookup;
import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.inventory.domain.InventoryLedger;
import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.inventory.repository.AllocationRepository;
import com.invsys.modules.inventory.repository.InventoryLedgerRepository;
import com.invsys.modules.inventory.repository.InventoryLevelRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class AllocationLookupAdapter implements AllocationLookup {

    private final AllocationRepository repository;

    AllocationLookupAdapter(AllocationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Allocation> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Allocation> findByTenantIdAndId(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id);
    }

    @Override
    public List<Allocation> findBySalesOrderLineIdAndStatus(UUID salesOrderLineId, String status) {
        return repository.findBySalesOrderLineIdAndStatus(salesOrderLineId, status);
    }

    @Override
    public List<Allocation> findByTenantIdAndVariantIdAndStatus(UUID tenantId, UUID variantId, String status) {
        return repository.findByTenantIdAndVariantIdAndStatus(tenantId, variantId, status);
    }

    @Override
    public Allocation save(Allocation allocation) {
        return repository.save(allocation);
    }

    @Override
    public Allocation saveAndFlush(Allocation allocation) {
        return repository.saveAndFlush(allocation);
    }
}

@Component
class InventoryLevelLookupAdapter implements InventoryLevelLookup {

    private final InventoryLevelRepository repository;

    InventoryLevelLookupAdapter(InventoryLevelRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<InventoryLevel> findAvailableForAllocation(UUID tenantId, UUID variantId, List<UUID> locationIds) {
        return repository.findAvailableForAllocation(tenantId, variantId, locationIds);
    }

    @Override
    public List<InventoryLevel> findByTenantIdAndVariantId(UUID tenantId, UUID variantId) {
        return repository.findByTenantIdAndVariantId(tenantId, variantId);
    }

    @Override
    public Optional<InventoryLevel> lockLevelForAllocation(
            UUID tenantId, UUID variantId, UUID locationId, UUID lotId) {
        return repository.lockLevelForAllocation(tenantId, variantId, locationId, lotId);
    }
}

@Component
class InventoryLedgerLookupAdapter implements InventoryLedgerLookup {

    private final InventoryLedgerRepository repository;

    InventoryLedgerLookupAdapter(InventoryLedgerRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<InventoryLedger> findByTenantIdAndReferenceTypeAndReferenceId(
            UUID tenantId, String referenceType, UUID referenceId) {
        return repository.findByTenantIdAndReferenceTypeAndReferenceId(tenantId, referenceType, referenceId);
    }
}
