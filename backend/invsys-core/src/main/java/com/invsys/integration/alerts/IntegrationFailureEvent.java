package com.invsys.integration.alerts;

import java.util.UUID;

/**
 * Raised when an external integration fails (signature, OAuth expiry, HTTP 401/403/500, etc.).
 */
public record IntegrationFailureEvent(
        UUID tenantId,
        String system,
        String reason,
        String detail,
        UUID entityId,
        boolean forceDispatch
) {
    public IntegrationFailureEvent(UUID tenantId, String system, String reason, String detail) {
        this(tenantId, system, reason, detail, null, false);
    }

    public IntegrationFailureEvent(UUID tenantId, String system, String reason, String detail, UUID entityId) {
        this(tenantId, system, reason, detail, entityId, false);
    }
}
