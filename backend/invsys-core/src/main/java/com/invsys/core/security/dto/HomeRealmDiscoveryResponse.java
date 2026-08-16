package com.invsys.core.security.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HomeRealmDiscoveryResponse(
        UUID tenantId,
        String ssoType,
        String ssoUrl,
        @JsonProperty("isPasswordAllowed") boolean isPasswordAllowed,
        String companyName
) {
    public static HomeRealmDiscoveryResponse passwordOnly() {
        return new HomeRealmDiscoveryResponse(null, null, null, true, null);
    }
}
