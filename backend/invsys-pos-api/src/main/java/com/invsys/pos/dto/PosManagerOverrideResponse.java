package com.invsys.pos.dto;

import java.util.List;
import java.util.UUID;

public record PosManagerOverrideResponse(
        UUID tenantId,
        List<ManagerPin> managers
) {
    public record ManagerPin(UUID managerId, String pinHash) {
    }
}
