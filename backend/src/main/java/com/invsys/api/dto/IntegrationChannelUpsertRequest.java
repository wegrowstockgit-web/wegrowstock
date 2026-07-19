package com.invsys.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record IntegrationChannelUpsertRequest(
        @NotBlank String channelType,
        String status,
        Map<String, String> credentials,
        Map<String, Object> settings
) {
}
