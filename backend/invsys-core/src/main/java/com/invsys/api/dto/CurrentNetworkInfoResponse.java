package com.invsys.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CurrentNetworkInfoResponse(
        String clientIp,
        String suggestedCidr,
        @JsonProperty("isPrivateNetwork") boolean privateNetwork,
        String networkHint
) {
}
