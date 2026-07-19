package com.invsys.integration.inbound;

import com.invsys.common.ApiException;
import com.invsys.service.EdiTranslationEngine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Translates inbound X12 850 flat files (AS2) via {@link EdiTranslationEngine} into the CDM.
 */
@Component
public class EdiOrderAdapter implements ExternalOrderAdapter {

    public static final String HEADER_TRADING_PARTNER_ID = "X-Trading-Partner-Id";

    private final EdiTranslationEngine ediTranslationEngine;

    public EdiOrderAdapter(EdiTranslationEngine ediTranslationEngine) {
        this.ediTranslationEngine = ediTranslationEngine;
    }

    @Override
    public boolean supports(String channelType) {
        if (channelType == null) {
            return false;
        }
        String key = channelType.trim().toUpperCase(Locale.ROOT);
        return "EDI".equals(key) || "AS2".equals(key) || "X12".equals(key);
    }

    @Override
    public CanonicalInboundOrder translate(String rawPayload, Map<String, String> headers) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "EDI 850 payload is required");
        }
        UUID partnerId = resolvePartnerId(headers);
        EdiTranslationEngine.InboundOrder inbound = ediTranslationEngine.parseInbound850(partnerId, rawPayload);

        List<CanonicalOrderLine> lines = inbound.lines().stream()
                .map(l -> new CanonicalOrderLine(
                        l.sku(),
                        l.quantity() != null ? l.quantity() : BigDecimal.ONE,
                        BigDecimal.ZERO))
                .toList();

        String customerId = inbound.customerId() != null ? inbound.customerId().toString() : null;
        return new CanonicalInboundOrder(
                inbound.poNumber(),
                ChannelSource.EDI,
                customerId,
                CanonicalAddress.empty(),
                CanonicalAddress.empty(),
                lines);
    }

    private UUID resolvePartnerId(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Header " + HEADER_TRADING_PARTNER_ID + " is required for EDI inbound");
        }
        String raw = null;
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && HEADER_TRADING_PARTNER_ID.equalsIgnoreCase(e.getKey())) {
                raw = e.getValue();
                break;
            }
            if (e.getKey() != null && "tradingPartnerId".equalsIgnoreCase(e.getKey())) {
                raw = e.getValue();
                break;
            }
        }
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Header " + HEADER_TRADING_PARTNER_ID + " is required for EDI inbound");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "Invalid trading partner id").withProperty("value", raw);
        }
    }
}
