package com.invsys.modules.inventory.api;

import com.invsys.modules.inventory.domain.InventoryLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelLookup {

    List<InventoryLevel> findAvailableForAllocation(UUID tenantId, UUID variantId, List<UUID> locationIds);

    List<InventoryLevel> findByTenantIdAndVariantId(UUID tenantId, UUID variantId);

    Optional<InventoryLevel> lockLevelForAllocation(UUID tenantId, UUID variantId, UUID locationId, UUID lotId);
}
