package com.invsys.api.dto;

import com.invsys.domain.InventoryLevel;
import com.invsys.domain.ProductVariant;

import java.util.List;
import java.util.UUID;

public record ScanLookupResponse(
        ProductVariant variant,
        List<InventoryLevel> levels,
        UUID defaultLocationId,
        String defaultLocationPath
) {
}
