package com.invsys.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseSpendRow(
        UUID purchaseOrderId,
        String number,
        String supplierName,
        String status,
        BigDecimal totalSpend
) {
}
