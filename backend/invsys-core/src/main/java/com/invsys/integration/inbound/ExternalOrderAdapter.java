package com.invsys.integration.inbound;

import java.util.Map;

/**
 * Translates a channel-specific raw payload into the unified {@link CanonicalInboundOrder}.
 */
public interface ExternalOrderAdapter {

    CanonicalInboundOrder translate(String rawPayload, Map<String, String> headers);

    boolean supports(String channelType);
}
