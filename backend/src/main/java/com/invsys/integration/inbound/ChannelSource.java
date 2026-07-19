package com.invsys.integration.inbound;

/**
 * Agnostic channel identity for inbound commerce / EDI orders.
 */
public enum ChannelSource {
    SHOPIFY,
    AMAZON,
    EDI;

    public static ChannelSource fromPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("channelSource is required");
        }
        return ChannelSource.valueOf(raw.trim().toUpperCase());
    }
}
