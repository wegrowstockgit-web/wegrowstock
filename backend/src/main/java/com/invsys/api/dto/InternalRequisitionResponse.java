package com.invsys.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InternalRequisitionResponse(
        UUID id,
        String requisitionNumber,
        UUID costCenterId,
        String costCenterCode,
        UUID requestedByUserId,
        String status,
        Instant createdAt,
        List<InternalRequisitionLineResponse> lines
) {
}
