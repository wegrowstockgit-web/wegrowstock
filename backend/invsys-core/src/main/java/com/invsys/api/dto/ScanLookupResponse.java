package com.invsys.api.dto;

import com.invsys.modules.inventory.domain.InventoryLevel;
import com.invsys.modules.catalog.domain.ProductVariant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Barcode / GS1-128 scan payload with catalog hit plus pre-filled tracking and dimensional fields
 * so warehouse UIs can skip manual lot / expiry / qty entry.
 */
public record ScanLookupResponse(
        ProductVariant variant,
        List<InventoryLevel> levels,
        UUID defaultLocationId,
        String defaultLocationPath,
        String gtin,
        String lotNumber,
        LocalDate expiryDate,
        String serialNumber,
        BigDecimal variableQuantity,
        Map<String, String> gs1Elements,
        String primaryMediaUrl,
        BigDecimal weight,
        String weightUnit,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String dimUnit,
        Map<String, Object> dims
) {
    public ScanLookupResponse(ProductVariant variant,
                              List<InventoryLevel> levels,
                              UUID defaultLocationId,
                              String defaultLocationPath) {
        this(variant, levels, defaultLocationId, defaultLocationPath,
                null, null, null, null, null, Map.of(), null,
                variant != null ? variant.getWeight() : null,
                variant != null ? variant.getWeightUnit() : null,
                variant != null ? variant.getLength() : null,
                variant != null ? variant.getWidth() : null,
                variant != null ? variant.getHeight() : null,
                variant != null ? variant.getDimUnit() : null,
                variant != null ? variant.getDims() : Map.of());
    }

    public static ScanLookupResponse of(ProductVariant variant,
                                        List<InventoryLevel> levels,
                                        UUID defaultLocationId,
                                        String defaultLocationPath,
                                        String gtin,
                                        String lotNumber,
                                        LocalDate expiryDate,
                                        String serialNumber,
                                        BigDecimal variableQuantity,
                                        Map<String, String> gs1Elements,
                                        String primaryMediaUrl) {
        return new ScanLookupResponse(
                variant,
                levels,
                defaultLocationId,
                defaultLocationPath,
                gtin,
                lotNumber,
                expiryDate,
                serialNumber,
                variableQuantity,
                gs1Elements != null ? gs1Elements : Map.of(),
                primaryMediaUrl,
                variant.getWeight(),
                variant.getWeightUnit(),
                variant.getLength(),
                variant.getWidth(),
                variant.getHeight(),
                variant.getDimUnit(),
                variant.getDims() != null ? variant.getDims() : Map.of());
    }
}
