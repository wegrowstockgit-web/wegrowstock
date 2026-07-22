package com.invsys.integration.inbound;

/**
 * Channel-agnostic postal address snapshot for inbound orders.
 */
public record CanonicalAddress(
        String name,
        String line1,
        String line2,
        String city,
        String region,
        String postalCode,
        String country
) {
    public static CanonicalAddress empty() {
        return new CanonicalAddress(null, null, null, null, null, null, null);
    }
}
