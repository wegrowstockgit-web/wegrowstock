package com.invsys.api.dto;

import com.invsys.domain.InventoryLevel;
import com.invsys.domain.ProductVariant;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScanLookupResponse(
        ProductVariant variant,
        List<InventoryLevel> levels,
        UUID defaultLocationId,
        String defaultLocationPath,
        String gtin,
        String lotNumber,
        LocalDate expiryDate,
        String serialNumber,
        Map<String, String> gs1Elements
) {
    public ScanLookupResponse(ProductVariant variant,
                              List<InventoryLevel> levels,
                              UUID defaultLocationId,
                              String defaultLocationPath) {
        this(variant, levels, defaultLocationId, defaultLocationPath, null, null, null, null, Map.of());
    }
}
