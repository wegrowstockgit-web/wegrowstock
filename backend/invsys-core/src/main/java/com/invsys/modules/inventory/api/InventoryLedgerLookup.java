package com.invsys.modules.inventory.api;

import com.invsys.modules.inventory.domain.InventoryLedger;

import java.util.List;
import java.util.UUID;

public interface InventoryLedgerLookup {

    List<InventoryLedger> findByTenantIdAndReferenceTypeAndReferenceId(
            UUID tenantId, String referenceType, UUID referenceId);
}
