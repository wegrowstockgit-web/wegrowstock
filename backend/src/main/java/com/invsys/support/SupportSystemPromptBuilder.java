package com.invsys.support;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dynamically prepends role + route directives for Spring AI {@code ChatClient} system blocks
 * (and the heuristic grounded composer).
 */
public final class SupportSystemPromptBuilder {

    private SupportSystemPromptBuilder() {
    }

    public static String build(List<String> roles, String route, List<SupportKnowledgeChunk> retrieved) {
        return build(roles, route, retrieved, Map.of(), Map.of());
    }

    public static String build(
            List<String> roles,
            String route,
            List<SupportKnowledgeChunk> retrieved,
            Map<String, Object> pageContext
    ) {
        return build(roles, route, retrieved, pageContext, Map.of());
    }

    public static String build(
            List<String> roles,
            String route,
            List<SupportKnowledgeChunk> retrieved,
            Map<String, Object> pageContext,
            Map<String, Object> pageState
    ) {
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
                    - ROLE SHIELD: The user is a PICKER on a handheld. Prioritize scanner-wedge and directed \
                    putaway/pick steps. NEVER instruct them to open desktop admin tabs (Settings users/SSO, \
                    billing, creating POs from an office keyboard).
                    """);
        }
        if (has(roles, "B2B_CUSTOMER")) {
            roleRules.append("""
                    - ROLE SHIELD: B2B_CUSTOMER showroom only — catalog, cart, checkout, order status. Never \
                    reveal warehouse maps, bins, ledger tables, or allocation internals.
                    """);
        }
        if (has(roles, "WAREHOUSE_MANAGER") || has(roles, "ADMIN") || has(roles, "OWNER")) {
            roleRules.append("""
                    - Managers/admins may receive office + floor guidance including exceptions, adjustments, \
                    cycle counts, allocation, and wave release. NEVER tell an office role to run a hardware \
                    scanner wedge sequence meant for a handheld PICKER.
                    """);
        }

        String pageBlock = formatPageContext(pageContext);
        String stateBlock = formatPageState(pageState);

        return """
                You are the InventorySystem Operations Instructor — a warm, clear, non-technical coach.

                The requesting user possesses the role(s) [%s]. They are viewing screen [%s].
                Use retrieved fragments, the localized page playbook, and the live page state to teach them \
                end-to-end — not just to answer passively.

                Language: human-centered, short sentences, exact on-screen button labels in **bold**.

                Formatting rules (always):
                1. Start with one-sentence **Diagnosis** (meaning of what they see / the blocker).
                2. Give an explicit numbered **Action plan** (Step 1…N) with exact button labels.
                3. Include a **↺ Reversal Guide** explaining how to undo safely without corrupting the \
                   append-only ledger (ERROR_CORRECTION / OFFLINE_CONFLICT_OVERRIDE / Un-allocate / Discard).
                4. Include a **👥 Downstream Impact** section: what other roles/devices see next.

                Action chips (propose in narrative when helpful; the platform may also emit structured chips):
                - NAVIGATE to a route (e.g. /purchase-orders, /sales-orders)
                - SPOTLIGHT a CSS selector / data-tour (e.g. [data-tour='btn-unallocate'])

                Rules:
                - Answer only from retrieved fragments, page playbook, page state, and these directives. If \
                  unknown, say so and suggest an in-app path.
                - Never instruct deleting append-only inventory_ledger history.
                %s
                %s
                %s
                Retrieved knowledge:
                %s
                """.formatted(
                roleLabel,
                routeLabel,
                roleRules,
                pageBlock.isBlank() ? "" : pageBlock + "\n",
                stateBlock.isBlank() ? "" : stateBlock + "\n",
                fragments.isBlank() ? "(none)" : fragments);
    }

    @SuppressWarnings("unchecked")
    static String formatPageContext(Map<String, Object> pageContext) {
        if (pageContext == null || pageContext.isEmpty()) {
            return "";
        }
        String title = stringVal(pageContext.get("title"));
        String purpose = stringVal(pageContext.get("purpose"));
        StringBuilder sb = new StringBuilder("Localized page playbook");
        if (!title.isBlank()) {
            sb.append(" (").append(title).append(')');
        }
        sb.append(":\n");
        if (!purpose.isBlank()) {
            sb.append("- Purpose: ").append(purpose).append('\n');
        }
        appendList(sb, "Flow", pageContext.get("flow"));
        appendList(sb, "Reversals", pageContext.get("reversals"));
        appendList(sb, "Correlations", pageContext.get("correlations"));
        appendComponents(sb, pageContext.get("components"));
        return sb.toString().strip();
    }

    static String formatPageState(Map<String, Object> pageState) {
        if (pageState == null || pageState.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Live page state snapshot:\n");
        appendStateField(sb, "routePath", pageState.get("routePath"));
        appendStateField(sb, "activeWarehouseId", pageState.get("activeWarehouseId"));
        appendStateField(sb, "activeWarehouseName", pageState.get("activeWarehouseName"));
        appendStateField(sb, "activeFilter", pageState.get("activeFilter"));
        appendStateField(sb, "activeTab", pageState.get("activeTab"));
        appendStateField(sb, "selectedEntity", pageState.get("selectedEntity"));
        appendStateField(sb, "networkState", pageState.get("networkState"));
        appendStateField(sb, "quarantineCount", pageState.get("quarantineCount"));
        Object roles = pageState.get("userRoles");
        if (roles instanceof List<?> list && !list.isEmpty()) {
            sb.append("- userRoles: ").append(list).append('\n');
        }
        return sb.toString().strip();
    }

    private static void appendStateField(StringBuilder sb, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = String.valueOf(value).trim();
        if (!s.isBlank() && !"null".equals(s)) {
            sb.append("- ").append(key).append(": ").append(s).append('\n');
        }
    }

    private static void appendList(StringBuilder sb, String label, Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append("- ").append(label).append(":\n");
        for (Object item : list) {
            if (item != null && !String.valueOf(item).isBlank()) {
                sb.append("  • ").append(item).append('\n');
            }
        }
    }

    /**
     * Serializes granular component / column / status maps so the LLM can answer
     * questions like "What does ALLOCATED mean on this table?".
     */
    @SuppressWarnings("unchecked")
    static void appendComponents(StringBuilder sb, Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append("- Components:\n");
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String name = stringVal(map.get("name"));
            String description = stringVal(map.get("description"));
            String dataOrigin = stringVal(map.get("dataOrigin"));
            if (name.isBlank()) {
                continue;
            }
            sb.append("  • ").append(name);
            if (!description.isBlank()) {
                sb.append(": ").append(description);
            }
            if (!dataOrigin.isBlank()) {
                sb.append(" [origin=").append(dataOrigin).append(']');
            }
            sb.append('\n');

            Object statuses = map.get("statuses");
            if (statuses instanceof Map<?, ?> statusMap && !statusMap.isEmpty()) {
                sb.append("    Statuses:\n");
                for (Map.Entry<?, ?> entry : statusMap.entrySet()) {
                    String code = stringVal(entry.getKey());
                    String meaning = stringVal(entry.getValue());
                    if (!code.isBlank()) {
                        sb.append("      - ").append(code).append(": ").append(meaning).append('\n');
                    }
                }
            }

            Object columns = map.get("columns");
            if (columns instanceof List<?> colList && !colList.isEmpty()) {
                sb.append("    Columns:\n");
                for (Object col : colList) {
                    if (col instanceof Map<?, ?> colMap) {
                        String colName = stringVal(colMap.get("name"));
                        String purpose = stringVal(colMap.get("purpose"));
                        if (!colName.isBlank()) {
                            sb.append("      - ").append(colName);
                            if (!purpose.isBlank()) {
                                sb.append(": ").append(purpose);
                            }
                            sb.append('\n');
                        }
                    }
                }
            }
        }
    }

    private static String stringVal(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
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
