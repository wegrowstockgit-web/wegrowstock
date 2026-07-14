package com.invsys.api.dto;

import java.util.UUID;

public record LotTraceResponse(
        UUID lotId,
        String lotNumber,
        GenealogyNode upstream,
        GenealogyNode downstream
) {
}
