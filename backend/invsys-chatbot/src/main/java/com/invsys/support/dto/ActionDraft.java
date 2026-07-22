package com.invsys.support.dto;

import java.util.Locale;
import java.util.Map;

/**
 * Human-in-the-loop "Do It For Me" draft — never auto-executes without Approve.
 */
public record ActionDraft(
        String title,
        String description,
        String targetEndpoint,
        String httpMethod,
        Map<String, Object> payload
) {
    public ActionDraft {
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        targetEndpoint = targetEndpoint == null ? "" : targetEndpoint;
        httpMethod = normalizeHttpMethod(httpMethod);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** Convenience constructor defaulting {@code httpMethod} to POST. */
    public ActionDraft(
            String title,
            String description,
            String targetEndpoint,
            Map<String, Object> payload
    ) {
        this(title, description, targetEndpoint, "POST", payload);
    }

    private static String normalizeHttpMethod(String method) {
        if (method == null || method.isBlank()) {
            return "POST";
        }
        String upper = method.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "POST", "PATCH", "PUT", "DELETE", "GET" -> upper;
            default -> "POST";
        };
    }
}
