package com.invsys.support;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import com.invsys.domain.Role;
import com.invsys.support.dto.ActionDraft;

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
                    reveal warehouse maps, bin locations, or how staff reserve stock.
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
                You are Gemini 2.0 Flash operating as the Growstock Inventory Co-Pilot — a warm, clear, \
                non-technical Operations Instructor for warehouse and office staff.

                The requesting user has the role(s) [%s]. They are viewing screen [%s].
                Teach them using only what appears on screen and what teammates do next.

                ABSOLUTE LANGUAGE BAN (never break this):
                - NEVER mention API endpoints (e.g. /api/v1/...), HTTP status codes (409, 422, 202), \
                  database tables, SQL, JSON, Java class or service names, code files, CQRS, webhooks, \
                  IndexedDB, or how the system stores data behind the scenes.
                - NEVER explain architecture or infrastructure. Speak only about UI buttons, status badges, \
                  roles, and physical scanner steps.

                Formatting rules (always use these four sections):
                1. **Operational Diagnosis:** One-sentence plain-language state summary \
                   (use live tool results when an order number or SKU is mentioned).
                2. **Action Plan:** Numbered steps 1…N using exact on-screen button labels in **bold**.
                3. **↺ Ledger Safety & Reversal Rule:** How to safely undo or fix mistakes on that page \
                   (Cancel, Un-allocate, Discard, Skip & Flag, manager stock correction — never "delete history").
                4. **👥 Downstream Impact:** What happens next for other team roles or handheld devices.

                Tool calling (read-only CQRS; tenant is already enforced by the platform — never ask for tenantId):
                - Whenever the user query mentions a specific Sales Order number or SKU, invoke your \
                  diagnostic read tools first to gather live facts before responding. Rely strictly on \
                  data returned by your tools.
                - Sales Order number → call **checkOrderStatus** (e.g., SO-123).
                - SKU / item code → call **checkAvailableToPromise** \
                  (and **getLedgerHistorySummary** if they ask why stock changed or disappeared).
                - Never invent or guess stock numbers or order states.
                - Enforce a non-technical tone: never expose Java class names, database tables, or SQL \
                  queries in the user response.

                Temporal UI memory:
                - Review recentBreadcrumbs in the page state (newest last).
                - If recentBreadcrumbs contains a TOAST_ERROR or SCAN_REJECTED event, prioritize \
diagnosing why that specific action failed in plain English referencing the exact button or field label.
                - If they recently clicked a button that failed, explain what went wrong on screen \
                  using exact UI button labels.
                - If the user provides an image of a damaged barcode or shipping label, visually \
                  inspect it to extract the SKU, order number, or tracking details. Use your tools to \
                  cross-reference the extracted data against the inventory ledger. Respond with \
                  non-technical numbered steps 1…N using exact on-screen labels \
                  (e.g. 1. Open **Inventory → Lots**. 2. Print a replacement label). \
                  Never invent serial or lot numbers that are not clearly visible.

                Role tailoring:
                - PICKER: physical scanner-wedge workflows on the handheld; never desktop admin tabs.
                - WAREHOUSE_MANAGER / ADMIN / OWNER: desktop management tools; never tell them to run \
                  a picker-only scanner wedge sequence unless they are on a floor device.

                Generative UI action chips (allowed actions only: NAVIGATE, SPOTLIGHT, START_TOUR):
                - When guiding the user, generate actionable chips whenever appropriate \
                  (e.g., NAVIGATE to relevant pages or SPOTLIGHT key on-screen elements).
                - Ensure target routes match valid application routes \
                  (e.g. /purchase-orders, /sales-orders, /fulfillment).
                - SPOTLIGHT targets must use exact data-tour attribute selectors \
                  (e.g. [data-tour='btn-unallocate']).
                - START_TOUR → tour id (office, floor, receiving-to-allocation).
                Also return 2–3 short followUpQuestions as quick-reply chips.

                Human-in-the-loop ActionDraft (optional):
                - When a user asks to resolve an operational blocker (e.g., release unallocated stock, \
                  cancel a backorder, or trigger a recount), do NOT just write instructions. Generate a \
                  pre-filled actionDraft containing the exact targetEndpoint, httpMethod (POST/PATCH), \
                  and payload required so the user can execute it with one click \
                  (title, description, targetEndpoint, httpMethod, payload).
                - Never auto-execute — the operator must Approve & Execute in the UI.

                Rules:
                - Answer only from the page playbook, live page state, tool results, and retrieved tips.
                - If unknown, say so and point to an in-app screen name — not a technical system.
                %s
                %s
                %s
                %s
                Retrieved operational tips (rewrite into plain language; never quote technical jargon):
                %s
                """.formatted(
                roleLabel,
                routeLabel,
                roleRules,
                pageBlock.isBlank() ? "" : pageBlock + "\n",
                stateBlock.isBlank() ? "" : stateBlock + "\n",
                formatTelemetryBlock(pageState),
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
        String dataOrigin = stringVal(pageContext.get("dataOrigin"));
        if (!dataOrigin.isBlank()) {
            sb.append("- Where this comes from: ").append(dataOrigin).append('\n');
        }
        Object whoCanUse = pageContext.get("whoCanUse");
        if (whoCanUse instanceof List<?> labels && !labels.isEmpty()) {
            appendList(sb, "Who can use this page", whoCanUse);
        } else {
            appendList(sb, "Who can use this page", pageContext.get("rolePermissions"));
        }
        Object steps = pageContext.get("stepByStepFlow");
        if (!(steps instanceof List<?> stepList) || stepList.isEmpty()) {
            steps = pageContext.get("flow");
        }
        appendList(sb, "Step-by-step", steps);
        Object undo = pageContext.get("howToUndo");
        if (!(undo instanceof List<?> undoList) || undoList.isEmpty()) {
            undo = pageContext.get("reversals");
        }
        appendList(sb, "How to undo", undo);
        appendList(sb, "Who else this affects", pageContext.get("correlations"));
        appendComponents(sb, pageContext.get("components"));
        Object glossary = pageContext.get("glossary");
        if (glossary instanceof Map<?, ?> map && !map.isEmpty()) {
            sb.append("- Glossary:\n");
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String term = stringVal(entry.getKey());
                String meaning = stringVal(entry.getValue());
                if (!term.isBlank()) {
                    sb.append("  • ").append(term).append(": ").append(meaning).append('\n');
                }
            }
        }
        return sb.toString().strip();
    }

    static String formatPageState(Map<String, Object> pageState) {
        if (pageState == null || pageState.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Live page state snapshot:\n");
        appendStateField(sb, "routePath", pageState.get("routePath"));
        appendStateField(sb, "pathname", pageState.get("pathname"));
        appendStateField(sb, "search", pageState.get("search"));
        appendStateField(sb, "activeWarehouseId", pageState.get("activeWarehouseId"));
        appendStateField(sb, "activeWarehouseName", pageState.get("activeWarehouseName"));
        appendStateField(sb, "activeFilter", pageState.get("activeFilter"));
        appendStateField(sb, "activeTab", pageState.get("activeTab"));
        Object selected = pageState.get("selectedEntityId");
        if (selected == null) {
            selected = pageState.get("selectedEntity");
        }
        appendStateField(sb, "selectedEntityId", selected);
        Object network = pageState.get("networkPhase");
        if (network == null) {
            network = pageState.get("networkState");
        }
        appendStateField(sb, "networkPhase", network);
        appendStateField(sb, "quarantineCount", pageState.get("quarantineCount"));
        appendStateField(sb, "trace_id", firstNonBlank(
                pageState.get("trace_id"), pageState.get("traceId"), pageState.get("lastRequestId")));
        appendStateField(sb, "lastHttpErrorStatus", firstNonBlank(
                pageState.get("lastHttpErrorStatus"), pageState.get("lastErrorStatus")));
        appendStateField(sb, "lastHttpErrorMessage", firstNonBlank(
                pageState.get("lastHttpErrorMessage"), pageState.get("lastErrorMessage")));
        Object roles = pageState.get("userRoles");
        if (roles instanceof List<?> list && !list.isEmpty()) {
            sb.append("- userRoles: ").append(list).append('\n');
        }
        Object recent = pageState.get("recentBreadcrumbs");
        if (!(recent instanceof List<?>)) {
            recent = pageState.get("recentUiActions");
        }
        if (recent instanceof List<?> actions && !actions.isEmpty()) {
            sb.append("- recentBreadcrumbs (newest last):\n");
            int start = Math.max(0, actions.size() - 5);
            for (int i = start; i < actions.size(); i++) {
                Object item = actions.get(i);
                if (item instanceof Map<?, ?> map) {
                    String type = stringVal(map.get("actionType"));
                    String label = stringVal(map.get("elementLabel"));
                    String err = stringVal(map.get("errorMessage"));
                    sb.append("  • ").append(type.isBlank() ? "ACTION" : type)
                            .append(" — ").append(label.isBlank() ? "(control)" : label);
                    if (!err.isBlank()) {
                        sb.append(" [error: ").append(err).append(']');
                    }
                    sb.append('\n');
                } else if (item != null) {
                    sb.append("  • ").append(item).append('\n');
                }
            }
        }
        return sb.toString().strip();
    }

    /**
     * When the SPA reports a recent failed mutation/query, coach the LLM to translate the
     * operational blocker into plain English (never echo HTTP codes or stack traces).
     */
    static String formatTelemetryBlock(Map<String, Object> pageState) {
        if (pageState == null || pageState.isEmpty()) {
            return "";
        }
        String status = stringVal(firstNonBlank(
                pageState.get("lastHttpErrorStatus"), pageState.get("lastErrorStatus")));
        String message = stringVal(firstNonBlank(
                pageState.get("lastHttpErrorMessage"), pageState.get("lastErrorMessage")));
        String trace = stringVal(firstNonBlank(
                pageState.get("trace_id"), pageState.get("traceId"), pageState.get("lastRequestId")));
        if (status.isBlank() && message.isBlank() && trace.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Recent operational blocker (translate to plain English; never quote codes):\n");
        if (!status.isBlank() || !message.isBlank()) {
            sb.append("- A recent floor/office action failed");
            if (!message.isBlank()) {
                sb.append(" with message hint: ").append(message);
            }
            sb.append(". Explain the business reason (e.g. bin locked by a blind cycle count, credit hold, empty bin) ");
            sb.append("and the on-screen fix — never mention HTTP status numbers.\n");
        }
        if (!trace.isBlank()) {
            sb.append("- Support reference id (for agents only; do not recite to the user): ").append(trace).append('\n');
        }
        return sb.toString().strip() + "\n";
    }

    private static Object firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank() && !"null".equals(String.valueOf(value))) {
                return value;
            }
        }
        return null;
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
                sb.append(" (where this comes from: ").append(dataOrigin).append(')');
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
