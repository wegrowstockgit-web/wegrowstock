package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CogsLedgerRow(
        String channel,
        UUID customerId,
        String customerName,
        String movementType,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal cogsAmount
) {
}
