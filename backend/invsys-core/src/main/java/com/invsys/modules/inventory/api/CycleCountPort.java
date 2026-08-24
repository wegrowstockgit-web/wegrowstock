package com.invsys.modules.inventory.api;

import java.util.UUID;

/**
 * Starts a warehouse cycle count. Purchasing uses this instead of the
 * inventory service package.
 */
public interface CycleCountPort {

    UUID startCount(UUID locationId);
}
