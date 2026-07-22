package com.invsys.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Resolution map returned by ingestion pre-flight (no ledger writes).
 */
public record PreflightResponse(
        List<PreflightRowDto> rows,
        Map<ImportRowStatus, Long> statusCounts,
        List<String> missingSkus,
        List<String> missingLocationPaths,
        String fileChecksumSha256
) {
}
