package com.invsys.pos.dto;

import java.util.List;
import java.util.UUID;

public record PosSyncResponse(
        int accepted,
        int duplicates,
        List<RejectedReceipt> rejected
) {
    public record RejectedReceipt(UUID receiptId, String reason) {
    }
}
