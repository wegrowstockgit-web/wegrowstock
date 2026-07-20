package com.invsys.support;

import java.util.Map;

/**
 * Structured UI action streamed to the chat widget (confirm-before-execute).
 */
public record SupportActionProposal(
        String type,
        String action,
        String label,
        Map<String, String> params
) {
    public static SupportActionProposal button(String action, String label, Map<String, String> params) {
        return new SupportActionProposal("action_button", action, label, params == null ? Map.of() : Map.copyOf(params));
    }
}
