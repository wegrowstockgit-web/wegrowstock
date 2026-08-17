package com.invsys.pos.dto;

import java.util.List;
import java.util.UUID;

public record PosAuditSyncResponse(
        int accepted,
        int duplicates,
        List<RejectedEvent> rejected
) {
    public record RejectedEvent(UUID eventId, String reason) {
    }
}
