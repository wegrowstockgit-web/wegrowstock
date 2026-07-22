package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VariantListItemResponse(
        UUID id,
        String sku,
        String name,
        String barcode,
        BigDecimal onHand,
        BigDecimal allocated,
        BigDecimal atp,
        BigDecimal price,
        String currency,
        boolean externalSyncEnabled,
        BigDecimal weight,
        String weightUnit,
        BigDecimal length,
        BigDecimal width,
        BigDecimal height,
        String dimUnit,
        UUID defaultSupplierId,
        int supplierLeadTimeDays,
        BigDecimal reorderPoint,
        BigDecimal reorderQty,
        String primaryMediaUrl,
        boolean isLotTracked,
        String hsTariffCode,
        String countryOfOrigin,
        boolean isHazmat,
        Integer palletTie,
        Integer palletHigh,
        String storageTempZone,
        boolean isFragile,
        String abcClassification,
        String lifecycleStatus
) {
}
