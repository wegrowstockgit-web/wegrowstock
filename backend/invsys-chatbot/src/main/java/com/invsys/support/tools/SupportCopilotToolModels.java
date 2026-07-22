package com.invsys.support.tools;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request/response records for read-only Support Copilot CQRS tools.
 *
 * <p>Intentionally omit {@code tenantId} — tools must resolve tenancy from
 * {@link com.invsys.core.tenancy.TenantContext} only.
 */
public final class SupportCopilotToolModels {

    private SupportCopilotToolModels() {
    }

    /** SKU + optional warehouse scope. Never includes tenantId. */
    public record AtpRequest(String sku, String warehouseId) {
    }

    public record AtpResponse(
            String sku,
            BigDecimal onHand,
            BigDecimal allocated,
            BigDecimal availableToPromise,
            String nextInboundPoNumber
    ) {
    }

    public record OrderStatusRequest(String orderNumber) {
    }

    public record OrderStatusResponse(
            String orderNumber,
            String status,
            String holdReason,
            String missingSku
    ) {
    }

    public record LedgerHistoryRequest(String sku, int limit) {
    }

    public record LedgerMovementView(
            String movementType,
            String quantityDelta,
            String reasonCode,
            String createdAt
    ) {
    }

    public record LedgerHistoryResponse(String sku, List<LedgerMovementView> movements) {
    }
}
