package com.invsys.support;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Dynamically prepends role + route directives for Spring AI {@code ChatClient} system blocks
 * (and the heuristic grounded composer).
 */
public final class SupportSystemPromptBuilder {

    private SupportSystemPromptBuilder() {
    }

    public static String build(List<String> roles, String route, List<SupportKnowledgeChunk> retrieved) {
        String roleLabel = roles == null || roles.isEmpty()
                ? "AUTHENTICATED"
                : roles.stream().map(r -> r.toUpperCase(Locale.ROOT)).collect(Collectors.joining(", "));
        String routeLabel = route == null || route.isBlank() ? "/" : route;
        String fragments = retrieved.stream()
                .map(c -> "### " + c.title() + "\n" + c.body())
                .collect(Collectors.joining("\n\n"));

        StringBuilder roleRules = new StringBuilder();
        if (has(roles, "PICKER") && exclusivePicker(roles)) {
            roleRules.append("""
                    - The user is a PICKER. Prioritize tactile, scanner-wedge troubleshooting and inbound/putaway \
                    steps. Completely omit desktop administrative steps such as creating purchase orders, \
                    editing billing, or configuring SSO.
                    """);
        }
        if (has(roles, "B2B_CUSTOMER")) {
            roleRules.append("""
                    - The user is a B2B_CUSTOMER in the showroom. Only discuss catalog, cart, checkout, and \
                    showroom order status. Never reveal warehouse maps, bin locations, ledger tables, or \
                    internal allocation details.
                    """);
        }
        if (has(roles, "WAREHOUSE_MANAGER") || has(roles, "ADMIN") || has(roles, "OWNER")) {
            roleRules.append("""
                    - Managers/admins may receive office + floor guidance including exceptions, adjustments, \
                    cycle counts, allocation, and wave release.
                    """);
        }

        return """
                You are the InventorySystem role-aware operations copilot.

                The requesting user possesses the role(s) [%s]. They are viewing screen [%s].
                Use the retrieved vector fragments below to provide an answer tailored to their operational \
                clearance level.

                Rules:
                - Answer only from the retrieved fragments and these directives. If unknown, say you do not have \
                  that information and suggest an in-app path (Settings, Exceptions, Fulfillment).
                - Keep answers short, actionable, and numbered when listing steps.
                %s
                Retrieved knowledge:
                %s
                """.formatted(roleLabel, routeLabel, roleRules, fragments.isBlank() ? "(none)" : fragments);
    }

    private static boolean has(List<String> roles, String role) {
        if (roles == null) {
            return false;
        }
        return roles.stream().anyMatch(r -> role.equalsIgnoreCase(r));
    }

    private static boolean exclusivePicker(List<String> roles) {
        return roles != null
                && !roles.isEmpty()
                && roles.stream().allMatch(r -> "PICKER".equalsIgnoreCase(r));
    }
}
