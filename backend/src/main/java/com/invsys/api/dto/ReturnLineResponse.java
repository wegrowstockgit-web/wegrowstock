package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnLineResponse(
        UUID id,
        UUID returnId,
        UUID salesOrderLineId,
        String sku,
        String productName,
        BigDecimal quantityExpected,
        BigDecimal quantityReceived,
        String disposition,
        String putawayTarget,
        String reasonCode,
        UUID mediaObjectId,
        String evidenceUrl
) {
}
