package com.invsys.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BomResponse(
        UUID id,
        UUID parentVariantId,
        String parentSku,
        String parentName,
        String name,
        @JsonProperty("isActive") boolean active,
        @JsonProperty("autoAssemble") boolean autoAssemble,
        List<BomLineResponse> lines,
        Instant createdAt
) {
}
