package com.invsys.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortalInvoiceResponse(
        UUID id,
        String number,
        String status,
        BigDecimal total,
        String currency,
        Instant dueAt
) {
}
