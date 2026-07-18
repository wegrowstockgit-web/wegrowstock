package com.invsys.api.dto;

import java.util.UUID;

/**
 * One CSV row after pre-flight resolution (identified match vs missing references).
 */
public record PreflightRowDto(
        int rowNumber,
        String sku,
        String name,
        String locationPath,
        ImportRowStatus status,
        String detail,
        UUID matchedVariantId,
        UUID matchedLocationId
) {
}
