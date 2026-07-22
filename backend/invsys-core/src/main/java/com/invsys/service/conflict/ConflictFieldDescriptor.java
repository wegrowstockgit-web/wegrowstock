package com.invsys.service.conflict;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One form field contract attached to a parked offline conflict ({@code schema_metadata_json}).
 */
public record ConflictFieldDescriptor(
        String key,
        String label,
        String type,
        boolean mutable,
        Map<String, Object> constraints
) {
    public ConflictFieldDescriptor {
        if (constraints == null) {
            constraints = Map.of();
        } else {
            constraints = Map.copyOf(new LinkedHashMap<>(constraints));
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("key", key);
        out.put("label", label);
        out.put("type", type);
        out.put("mutable", mutable);
        out.put("constraints", constraints);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static ConflictFieldDescriptor fromMap(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        Object constraintsObj = raw.get("constraints");
        Map<String, Object> constraints = constraintsObj instanceof Map<?, ?> m
                ? (Map<String, Object>) m
                : Map.of();
        return new ConflictFieldDescriptor(
                String.valueOf(raw.getOrDefault("key", "")),
                String.valueOf(raw.getOrDefault("label", "")),
                String.valueOf(raw.getOrDefault("type", "string")),
                Boolean.TRUE.equals(raw.get("mutable")) || "true".equalsIgnoreCase(String.valueOf(raw.get("mutable"))),
                constraints);
    }
}
