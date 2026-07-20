package com.invsys.service.conflict;

import com.invsys.domain.ConflictActionType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps parking context (URL + scan mode / action) into glove-friendly form field descriptors.
 * Managers never see raw JSON — only these labels/types.
 */
@Component
public class ConflictSchemaMetadataGenerator {

    public List<ConflictFieldDescriptor> generate(String requestUrl, ConflictActionType actionType, Object body) {
        ConflictActionType resolved = actionType != null ? actionType : inferFromUrl(requestUrl);
        if (resolved == null && body instanceof Map<?, ?> map) {
            Object mode = map.get("mode");
            if (mode != null) {
                resolved = ConflictActionType.fromScanMode(String.valueOf(mode));
            }
        }
        if (resolved == null) {
            return genericDescriptors();
        }
        return switch (resolved) {
            case INBOUND_RECEIVE -> inboundReceive();
            case OUTBOUND_PICK -> outboundPick();
            case CYCLE_COUNT -> cycleCount();
        };
    }

    public List<Map<String, Object>> generateAsMaps(
            String requestUrl, ConflictActionType actionType, Object body) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConflictFieldDescriptor d : generate(requestUrl, actionType, body)) {
            out.add(d.toMap());
        }
        return out;
    }

    private static ConflictActionType inferFromUrl(String requestUrl) {
        if (requestUrl == null) {
            return null;
        }
        String u = requestUrl.toLowerCase(Locale.ROOT);
        if (u.contains("/cycle-count") || u.contains("/count")) {
            return ConflictActionType.CYCLE_COUNT;
        }
        if (u.contains("/fulfillment/scan") || u.contains("/inbound")) {
            return null; // need mode from body
        }
        if (u.contains("/inventory/adjust")) {
            return ConflictActionType.CYCLE_COUNT;
        }
        return null;
    }

    private static List<ConflictFieldDescriptor> inboundReceive() {
        return List.of(
                number("quantity", "Corrected Quantity Count", 1),
                readonly("barcode", "Scanned Item Master GTIN"),
                readonly("warehouseId", "Destination Warehouse"),
                string("lotNumber", "Lot / Batch Number", true));
    }

    private static List<ConflictFieldDescriptor> outboundPick() {
        return List.of(
                number("quantity", "Corrected Pick Quantity", 1),
                readonly("barcode", "Scanned Item Master GTIN"),
                readonly("allocationId", "Original Allocation"),
                readonly("warehouseId", "Warehouse"));
    }

    private static List<ConflictFieldDescriptor> cycleCount() {
        return List.of(
                number("quantity", "Corrected Physical Count", 0),
                readonly("barcode", "Counted Item GTIN"),
                readonly("warehouseId", "Bin / Warehouse"));
    }

    private static List<ConflictFieldDescriptor> genericDescriptors() {
        return List.of(
                number("quantity", "Corrected Quantity", 1),
                readonly("barcode", "Scanned Barcode"));
    }

    private static ConflictFieldDescriptor number(String key, String label, Number min) {
        return new ConflictFieldDescriptor(key, label, "number", true, Map.of("min", min));
    }

    private static ConflictFieldDescriptor string(String key, String label, boolean mutable) {
        return new ConflictFieldDescriptor(key, label, "string", mutable, Map.of());
    }

    private static ConflictFieldDescriptor readonly(String key, String label) {
        return new ConflictFieldDescriptor(key, label, "string", false, Map.of());
    }
}
