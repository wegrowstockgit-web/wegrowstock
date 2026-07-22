package com.invsys.api.dto;

import java.util.UUID;

public record ChannelIntegrationResponse(
        UUID id,
        String platform,
        String shopIdentifier,
        String status
) {
}
