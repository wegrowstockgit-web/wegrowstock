package com.invsys.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.invsys.modules.inventory.domain.Allocation;

/**
 * Enforces the Operations Instructor response shape for heuristic (and LLM side) answers:
 * Diagnosis → numbered Action Plan → Ledger Safety & Reversal → Downstream Ripple,
 * plus NAVIGATE/SPOTLIGHT/START_TOUR chips and Socratic follow-ups.
 */
public final class OperationsInstructorFormatter {

    private OperationsInstructorFormatter() {
    }

    /**
     * Reformats free-form model output into the mandatory Operations Instructor sections.
     */
    public static String enrich(
            String answer,
            String question,
            List<String> roles,
            String route,
            Map<String, Object> pageState
    ) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean pickerOnly = roles != null && roles.size() == 1 && roles.contains("PICKER");
        boolean b2b = roles != null && roles.contains("B2B_CUSTOMER");
        return ensureInstructorShape(answer, q, pickerOnly, b2b, route);
    }

    static HeuristicSupportResult enrich(
            HeuristicSupportResult raw,
            String question,
            List<String> roles,
            String route,
            Map<String, Object> pageState
    ) {
        if (raw == null) {
            return HeuristicSupportResult.of("I do not have a grounded answer for that yet.");
        }
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        boolean pickerOnly = roles != null && roles.size() == 1 && roles.contains("PICKER");
        boolean b2b = roles != null && roles.contains("B2B_CUSTOMER");

        String markdown = ensureInstructorShape(raw.answer(), q, pickerOnly, b2b, route);
        List<SupportActionProposal> actions = new ArrayList<>(raw.actions());
        actions.addAll(suggestChips(q, route, pickerOnly, b2b, pageState));
        List<String> followUps = raw.followUps().isEmpty()
                ? suggestFollowUps(q, pickerOnly, b2b)
                : raw.followUps();
        return HeuristicSupportResult.of(markdown, actions, followUps);
    }

    static String ensureInstructorShape(
            String answer,
            String q,
            boolean pickerOnly,
            boolean b2b,
            String route
    ) {
        String body = answer == null ? "" : answer.strip();
        if (body.isBlank()) {
            body = "I need a bit more detail about what you see on screen.";
        }
        if (body.contains("**Operational Diagnosis")
                || body.contains("**Diagnosis")
                || body.contains("↺ Ledger Safety")
                || body.contains("↺ Reversal Guide")) {
            return body;
        }

        String diagnosis = firstSentence(body);
        String plan = toNumberedPlan(body, pickerOnly);
        String reversal = reversalGuide(q, pickerOnly, b2b);
        String downstream = downstreamImpact(q, pickerOnly, b2b, route);

        return """
                **Operational Diagnosis:** %s

                **Action Plan**
                %s

                **↺ Ledger Safety & Reversal Rule**
                %s

                **👥 Downstream Impact**
                %s
                """.formatted(diagnosis, plan, reversal, downstream).strip();
    }

    private static String firstSentence(String body) {
        int end = body.indexOf('.');
        if (end > 12 && end < 220) {
            return body.substring(0, end + 1).strip();
        }
        String line = body.lines().findFirst().orElse(body).strip();
        return line.length() > 180 ? line.substring(0, 177) + "…" : line;
    }

    private static String toNumberedPlan(String body, boolean pickerOnly) {
        List<String> steps = new ArrayList<>();
        for (String line : body.split("\n")) {
            String t = line.strip();
            if (t.matches("^\\d+\\.\\s+.+")) {
                steps.add(t.replaceFirst("^\\d+\\.\\s+", ""));
            } else if (t.startsWith("- ") || t.startsWith("• ")) {
                steps.add(t.substring(2).strip());
            }
        }
        if (steps.isEmpty()) {
            // Split on sentence boundaries for a short plan.
            String[] sentences = body.split("(?<=[.!?])\\s+");
            for (String s : sentences) {
                String t = s.strip();
                if (t.length() > 20) {
                    steps.add(t);
                }
                if (steps.size() >= 4) {
                    break;
                }
            }
        }
        if (steps.isEmpty()) {
            steps.add(pickerOnly
                    ? "Use your handheld scanner on the field highlighted on this screen, then follow the next directed prompt."
                    : "Use the primary action button on this page that matches the goal in your question.");
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String step : steps) {
            if (i > 5) {
                break;
            }
            sb.append(i++).append(". ").append(step.strip()).append('\n');
        }
        return sb.toString().strip();
    }

    private static String reversalGuide(String q, boolean pickerOnly, boolean b2b) {
        if (b2b) {
            return "Showroom orders cannot rewrite warehouse stock. If checkout failed, retry from Showroom → Cart; "
                    + "contact your supplier for order changes.";
        }
        if (pickerOnly) {
            return "Do not try to erase a scan. If a pick was wrong, use **Skip & Flag** or ask a manager "
                    + "to post a stock correction — history stays; corrections are added.";
        }
        if (q.contains("allocat")) {
            return "On Sales Orders, use **Un-allocate** before the wave ships. That releases reservations "
                    + "without erasing stock history. After ship, reverse via a return/RMA path.";
        }
        if (q.contains("ledger") || q.contains("reverse") || q.contains("undo") || q.contains("correction")) {
            return "Ask a manager to post a **stock correction** that undoes the mistake. "
                    + "History stays visible — nobody deletes past stock movements.";
        }
        if (q.contains("conflict") || q.contains("409") || q.contains("offline") || q.contains("parked")) {
            return "In the Conflict Panel, choose **Discard** to drop the parked scan, or **Approve & Re-process** "
                    + "after fixing stock — both keep a clear trail of who decided.";
        }
        return "Prefer on-screen actions such as **Cancel**, **Un-allocate**, or **Discard**. "
                + "Never ask anyone to erase stock history.";
    }

    private static String downstreamImpact(String q, boolean pickerOnly, boolean b2b, String route) {
        if (b2b) {
            return "Your order status updates in Showroom → Orders. Warehouse staff see the sales order for fulfillment separately.";
        }
        if (pickerOnly) {
            return "Completing a scan updates the wave task for your device. Managers see exceptions and progress on desktop; "
                    + "office staff should not run your scanner wedge sequence.";
        }
        if (q.contains("wave") || q.contains("release")) {
            return "Pickers' handhelds receive new tasks immediately. Staging and shipping see cartons once picks complete.";
        }
        if (q.contains("allocat")) {
            return "Allocation reserves FEFO lots. Wave release then creates picker tasks; credit holds block this until billing clears.";
        }
        if (q.contains("receive") || q.contains("inbound") || q.contains("landed")) {
            return "Receiving raises on-hand and can trigger cross-dock intercepts for backorders. Landed-cost surcharges "
                    + "roll into unit valuation for finance.";
        }
        String routeHint = route == null || route.isBlank() ? "this screen" : route;
        return "Other roles and devices refresh from the shared ledger after your change on " + routeHint + ".";
    }

    static List<SupportActionProposal> suggestChips(
            String q,
            String route,
            boolean pickerOnly,
            boolean b2b,
            Map<String, Object> pageState
    ) {
        List<SupportActionProposal> chips = new ArrayList<>();
        if (b2b) {
            chips.add(SupportActionProposal.navigate("Open Showroom Orders", "/showroom/orders"));
            return chips;
        }
        if (pickerOnly) {
            if (q.contains("inbound") || q.contains("receive") || q.contains("putaway")) {
                chips.add(SupportActionProposal.navigate("Open Fulfillment", "/fulfillment"));
                chips.add(SupportActionProposal.spotlight(
                        "Highlight scan input", "[data-tour='scanner-input'], [data-testid='barcode-scanner-input']"));
            } else {
                chips.add(SupportActionProposal.navigate("Open Fulfillment", "/fulfillment"));
            }
            return chips;
        }

        if (q.contains("purchase") || q.contains("inbound") || q.contains("receive") || q.contains("landed")) {
            chips.add(SupportActionProposal.navigate("Take me to Purchase Orders", "/purchase-orders"));
            if (q.contains("walkthrough") || q.contains("train") || q.contains("tour")
                    || q.contains("how do i receive") || q.contains("end to end") || q.contains("end-to-end")) {
                chips.add(SupportActionProposal.startTour(
                        "Start Route Walkthrough", "receiving-to-allocation"));
            }
        }
        if (q.contains("allocat") || q.contains("backorder") || q.contains("wave") || q.contains("sales")) {
            chips.add(SupportActionProposal.navigate("Take me to Sales Orders", "/sales-orders"));
            chips.add(SupportActionProposal.spotlight("Highlight Un-allocate", "[data-tour='btn-unallocate']"));
            if (q.contains("walkthrough") || q.contains("train") || q.contains("tour")
                    || q.contains("end to end") || q.contains("end-to-end") || q.contains("how do i allocate")) {
                chips.add(SupportActionProposal.startTour(
                        "Start Route Walkthrough", "receiving-to-allocation"));
            }
        }
        if (q.contains("walkthrough") || q.contains("start tour") || q.contains("show me around")
                || q.contains("train me")) {
            chips.add(SupportActionProposal.startTour(
                    "Start Route Walkthrough", "receiving-to-allocation"));
        }
        if (q.contains("conflict") || q.contains("offline") || q.contains("409")) {
            chips.add(SupportActionProposal.navigate("Open Exceptions", "/exceptions"));
            chips.add(SupportActionProposal.spotlight(
                    "Highlight Conflict Panel", "[data-tour='conflict-panel'], [data-testid='conflict-panel']"));
        }
        if (q.contains("cycle count") || q.contains("blind count")) {
            chips.add(SupportActionProposal.navigate("Open Cycle Counts", "/cycle-counts"));
        }
        if (chips.isEmpty() && route != null && route.contains("sales-orders")) {
            chips.add(SupportActionProposal.spotlight("Highlight Un-allocate", "[data-tour='btn-unallocate']"));
        }
        if (chips.isEmpty()) {
            String tab = pageState == null ? null : stringVal(pageState.get("activeTab"));
            if (tab != null && !tab.isBlank()) {
                chips.add(SupportActionProposal.navigate(
                        "Stay on current settings tab", "/settings?tab=" + tab));
            }
        }
        return chips;
    }

    static List<String> suggestFollowUps(String q, boolean pickerOnly, boolean b2b) {
        if (b2b) {
            return List.of(
                    "Where do I see my order status?",
                    "Why is an item out of stock in the catalog?",
                    "How do I retry a failed checkout?");
        }
        if (pickerOnly) {
            return List.of(
                    "What if the bin is empty?",
                    "How do I Skip & Flag a line?",
                    "What do I scan next?");
        }
        if (q.contains("conflict") || q.contains("offline")) {
            return List.of(
                    "When should I Discard vs Approve & Re-process?",
                    "Does resolving a conflict change the ledger?",
                    "How do I find the parked mutation?");
        }
        if (q.contains("allocat") || q.contains("backorder")) {
            return List.of(
                    "Why is this order BACKORDERED?",
                    "How do I Un-allocate safely?",
                    "What does FEFO change for the picker?");
        }
        return List.of(
                "How do I undo the last step safely?",
                "What status should I expect next?",
                "Who is affected on other devices?");
    }

    private static String stringVal(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
