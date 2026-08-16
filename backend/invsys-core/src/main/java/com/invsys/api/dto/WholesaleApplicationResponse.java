package com.invsys.api.dto;

import java.time.Instant;
import java.util.UUID;

public record WholesaleApplicationResponse(
        UUID id,
        String companyName,
        String taxId,
        String contactName,
        String email,
        String phone,
        String status,
        Instant createdAt,
        UUID customerId,
        String magicToken
) {
}
