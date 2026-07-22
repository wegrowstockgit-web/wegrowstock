package com.invsys.support;

import com.invsys.support.dto.ActionDraft;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Executes human-approved action drafts against a strict allow-list.
 * Prefer {@code supportAction} payloads that map to {@link SupportAgentTools}.
 */
@Service
public class SupportActionDraftExecutor {

    private static final Set<String> ALLOWED_AGENT_ACTIONS = Set.of(
            "generateCycleCount",
            "releaseWave");

    private final SupportAgentTools agentTools;

    public SupportActionDraftExecutor(SupportAgentTools agentTools) {
        this.agentTools = agentTools;
    }

    public Map<String, Object> execute(ActionDraft draft) {
        TenantContext.requireTenantId();
        if (draft == null) {
            return Map.of("ok", false, "error", "Missing action draft");
        }
        Object actionObj = draft.payload().get("supportAction");
        if (actionObj instanceof String action && ALLOWED_AGENT_ACTIONS.contains(action)) {
            Map<String, String> params = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : draft.payload().entrySet()) {
                if ("supportAction".equals(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                params.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
            try {
                Map<String, Object> result = agentTools.execute(action, params);
                Map<String, Object> out = new LinkedHashMap<>(result);
                out.putIfAbsent("ok", true);
                out.put("title", draft.title());
                return out;
            } catch (RuntimeException ex) {
                String message = ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Could not complete that change on the floor."
                        : ex.getMessage();
                return Map.of(
                        "ok", false,
                        "error", message,
                        "title", draft.title());
            }
        }

        String endpoint = draft.targetEndpoint() == null ? "" : draft.targetEndpoint().trim();
        if (!isAllowedEndpoint(endpoint)) {
            return Map.of(
                    "ok", false,
                    "error", "That change is not on the approved list. Use the on-screen button instead.");
        }
        return Map.of(
                "ok", true,
                "executed", false,
                "navigational", true,
                "message", "Draft approved. Follow the highlighted on-screen button to finish safely.",
                "targetEndpoint", endpoint,
                "title", draft.title());
    }

    static boolean isAllowedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        String lower = endpoint.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("/api/v1/")) {
            return false;
        }
        return lower.contains("/allocate")
                || lower.contains("/unallocate")
                || lower.contains("/un-allocate")
                || lower.contains("/release")
                || lower.contains("/claim")
                || lower.contains("/confirm")
                || lower.contains("/cancel")
                || lower.contains("/cycle-counts");
    }
}
