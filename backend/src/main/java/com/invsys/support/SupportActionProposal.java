package com.invsys.support;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured UI action streamed to the chat widget.
 * {@code action_button} = confirm-before-execute platform mutation;
 * {@code action_chip} = NAVIGATE / SPOTLIGHT instructor chips.
 */
public record SupportActionProposal(
        String type,
        String action,
        String label,
        Map<String, String> params
) {
    public SupportActionProposal {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public static SupportActionProposal button(String action, String label, Map<String, String> params) {
        return new SupportActionProposal("action_button", action, label, params == null ? Map.of() : Map.copyOf(params));
    }

    public static SupportActionProposal navigate(String label, String target) {
        return chip("NAVIGATE", label, target);
    }

    public static SupportActionProposal spotlight(String label, String target) {
        return chip("SPOTLIGHT", label, target);
    }

    public static SupportActionProposal chip(String action, String label, String target) {
        Map<String, String> params = new LinkedHashMap<>();
        if (target != null && !target.isBlank()) {
            params.put("target", target);
        }
        return new SupportActionProposal("action_chip", action, label, params);
    }

    public String target() {
        String t = params.get("target");
        return t == null ? "" : t;
    }
}
