package com.invsys.integration.inbound;

import java.util.List;

/**
 * Immutable canonical inbound order (CDM) shared by Shopify/Amazon webhooks and EDI 850.
 */
public record CanonicalInboundOrder(
        String externalOrderRef,
        ChannelSource channelSource,
        String customerIdentifier,
        CanonicalAddress billingAddress,
        CanonicalAddress shippingAddress,
        List<CanonicalOrderLine> lines
) {
    public CanonicalInboundOrder {
        lines = lines == null ? List.of() : List.copyOf(lines);
        billingAddress = billingAddress == null ? CanonicalAddress.empty() : billingAddress;
        shippingAddress = shippingAddress == null ? CanonicalAddress.empty() : shippingAddress;
    }
}
