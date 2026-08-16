package com.invsys.modules.inventory.api;

import com.invsys.modules.inventory.domain.Allocation;
import com.invsys.modules.inventory.domain.InventoryLedger;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Published inventory mutations for other bounded contexts (receiving, shipping).
 */
public interface InventoryOperations {

    InventoryLedger receive(UUID variantId, UUID locationId, UUID lotId, BigDecimal quantity,
                            String referenceType, UUID referenceId, BigDecimal unitCost);

    InventoryLedger receive(UUID variantId, UUID locationId, UUID lotId, String lotNumber,
                            BigDecimal quantity, String reasonCode, String referenceType, UUID referenceId,
                            BigDecimal unitCost, String serialCode, Map<String, Object> metadata);

    InventoryLedger ship(Allocation allocation, BigDecimal quantity);
}
