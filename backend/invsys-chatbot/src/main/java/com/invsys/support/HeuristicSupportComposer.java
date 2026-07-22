package com.invsys.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.invsys.domain.User;

/**
 * Grounded, role-aware answers for CI and environments without an OpenAI key.
 * Uses retrieved GraphRAG fragments + clearance rules; may emit confirm-gated action buttons.
 */
final class HeuristicSupportComposer {

    private static final Pattern ZONE_PATTERN = Pattern.compile(
            "(?:zone|aisle|bin|location)\\s*[:=]?\\s*([A-Za-z0-9._/-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WAVE_PATTERN = Pattern.compile(
            "(?:wave)\\s*[:=]?\\s*([0-9a-fA-F-]{36})",
            Pattern.CASE_INSENSITIVE);

    private HeuristicSupportComposer() {
    }

    static HeuristicSupportResult compose(
            String question,
            List<String> roles,
            String route,
            List<SupportKnowledgeChunk> retrieved,
            String systemPrompt
    ) {
        return compose(question, roles, route, retrieved, systemPrompt, Map.of());
    }

    static HeuristicSupportResult compose(
            String question,
            List<String> roles,
            String route,
            List<SupportKnowledgeChunk> retrieved,
            String systemPrompt,
            Map<String, Object> pageState
    ) {
        HeuristicSupportResult raw = composeRaw(question, roles, route, retrieved);
        return OperationsInstructorFormatter.enrich(raw, question, roles, route, pageState);
    }

    private static HeuristicSupportResult composeRaw(
            String question,
            List<String> roles,
            String route,
            List<SupportKnowledgeChunk> retrieved
    ) {
        String q = question.toLowerCase(Locale.ROOT);
        boolean pickerOnly = roles.size() == 1 && roles.contains("PICKER");
        boolean b2b = roles.contains("B2B_CUSTOMER");
        boolean manager = roles.contains("WAREHOUSE_MANAGER")
                || roles.contains("ADMIN")
                || roles.contains("OWNER");

        if (b2b && (q.contains("allocation") || q.contains("inventory") || q.contains("warehouse") || q.contains("bin"))) {
            return HeuristicSupportResult.of("""
                    As a B2B customer you shop in the showroom catalog and track orders under Showroom → Orders. \
                    You can see catalog availability and your order status there. Internal facility layout and \
                    operator stock-reservation details are not visible to customer accounts — that protects the \
                    operator's facility data.
                    """);
        }

        if (manager && (q.contains("cycle count") || q.contains("generate cycle") || q.contains("count zone"))) {
            String zone = extractZone(question);
            if (zone.isBlank()) {
                zone = "Aisle-4";
            }
            return HeuristicSupportResult.of(
                    "I can start a cycle count for " + zone
                            + ". Confirming posts a count worksheet for that bin so on-hand matches the floor.",
                    List.of(SupportActionProposal.button(
                            "generateCycleCount",
                            "Generate cycle count for " + zone,
                            Map.of("zoneId", zone))));
        }

        if (manager && (q.contains("release wave") || q.contains("release the wave"))) {
            String waveId = extractWaveId(question);
            List<SupportActionProposal> actions = new ArrayList<>();
            if (!waveId.isBlank()) {
                actions.add(SupportActionProposal.button(
                        "releaseWave",
                        "Release wave",
                        Map.of("waveId", waveId)));
            }
            SupportKnowledgeChunk doc = firstSlug(retrieved, "office-allocate-wave");
            String body = doc != null
                    ? trimBody(doc.body())
                    : "Release the wave from Sales Orders / Picking after allocation so pickers can claim tasks.";
            return HeuristicSupportResult.of(body, actions);
        }

        if (pickerOnly && (q.contains("inbound") || q.contains("receive") || q.contains("shipment") || q.contains("putaway"))) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "picker-inbound-receive");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        // Operational conflict playbooks (offline parking / Skip & Flag / cross-dock).
        if (q.contains("offline") || q.contains("sync conflict") || q.contains("parking")
                || q.contains("409") || q.contains("mutation queue") || q.contains("replay")
                || q.contains("conflict panel")) {
            SupportKnowledgeChunk panel = firstSlug(retrieved, "ops-offline-conflict-panel-resolve");
            if (panel != null) {
                return HeuristicSupportResult.of(trimBody(panel.body()));
            }
            SupportKnowledgeChunk doc = firstSlug(retrieved, "ops-offline-mutation-parking");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        if (q.contains("landed cost") || q.contains("surcharge") || q.contains("unit valuation")) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "ops-landed-cost-distribution");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        if (q.contains("fefo") || q.contains("credit limit") || q.contains("credit hold")) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "ops-fefo-allocation-credit-holds");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        if (q.contains("ledger") || q.contains("error_correction") || q.contains("append-only")
                || (q.contains("reverse") && q.contains("inventory"))) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "ops-append-only-ledger-reversals");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        if (q.contains("blind") || q.contains("cycle count escalate") || q.contains("pending_manager_review")) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "ops-blind-cycle-count-escalation");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        if (q.contains("status code") || (q.contains("what does") && (q.contains("draft") || q.contains("allocated")
                || q.contains("backordered") || q.contains("mean")))) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "ops-status-codes-po-so-invoice-rma");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        if (q.contains("skip") || q.contains("skip & flag") || q.contains("skip and flag")
                || ((q.contains("exception") || q.contains("empty bin") || q.contains("unpickable"))
                && !q.contains("damaged"))) {
            SupportKnowledgeChunk skip = firstSlug(retrieved, "ops-skip-and-flag-exceptions");
            if (skip != null) {
                return HeuristicSupportResult.of(trimBody(skip.body()));
            }
        }

        if (q.contains("cross-dock") || q.contains("cross dock") || q.contains("crossdock")
                || q.contains("staging lane") || (q.contains("backorder") && q.contains("receive"))) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "ops-cross-dock-intercept");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
            }
        }

        if (manager && (q.contains("damaged") || q.contains("exception") || q.contains("damage"))) {
            SupportKnowledgeChunk skip = firstSlug(retrieved, "ops-skip-and-flag-exceptions");
            SupportKnowledgeChunk damage = firstSlug(retrieved, "manager-damaged-exception");
            if (skip != null && damage != null) {
                return HeuristicSupportResult.of(
                        trimBody(skip.body())
                                + "\n\nRelated (GraphRAG): damaged-stock disposition after the exception:\n"
                                + trimBody(damage.body()));
            }
            if (damage != null) {
                return HeuristicSupportResult.of(trimBody(damage.body()));
            }
            if (skip != null) {
                return HeuristicSupportResult.of(trimBody(skip.body()));
            }
        }

        // Prefer page-local reversal guidance when the SPA injected System Context.
        if (q.contains("system context:") && (q.contains("reversal") || q.contains("undo") || q.contains("reverse")
                || q.contains("cancel") || q.contains("un-allocate") || q.contains("how do i undo"))) {
            String fromContext = extractReversalFromSystemContext(question);
            if (!fromContext.isBlank()) {
                return HeuristicSupportResult.of(
                        fromContext
                                + "\n\nNever erase stock history — ask a manager to post a correcting "
                                + "stock movement instead.");
            }
        }

        if (!retrieved.isEmpty()) {
            SupportKnowledgeChunk best = retrieved.getFirst();
            if (pickerOnly && best.slug().contains("office-create-po")) {
                SupportKnowledgeChunk floor = firstSlug(retrieved, "picker-inbound-receive");
                if (floor != null) {
                    return HeuristicSupportResult.of(trimBody(floor.body()));
                }
            }
            // Prefer graph-expanded allocate context when asking about allocation.
            if (q.contains("allocation") || q.contains("allocate")) {
                SupportKnowledgeChunk allocate = firstSlug(retrieved, "office-allocate-wave");
                SupportKnowledgeChunk inbound = firstSlug(retrieved, "picker-inbound-receive");
                if (allocate != null && inbound != null) {
                    return HeuristicSupportResult.of(
                            trimBody(allocate.body())
                                    + "\n\nRelated (GraphRAG): receiving/putaway unlocks the stock this allocation uses:\n"
                                    + trimBody(inbound.body()));
                }
            }
            return HeuristicSupportResult.of(trimBody(best.body()));
        }

        return HeuristicSupportResult.of(
                "I do not have a grounded answer for that yet. Try Fulfillment for scans, Sales Orders for "
                        + "allocation/waves, Exceptions for floor issues, or Showroom Orders if you are a B2B customer. "
                        + "(route=" + route + ")");
    }

    /** Pull undo guidance from the SPA-injected system context prefix. */
    static String extractReversalFromSystemContext(String question) {
        if (question == null) {
            return "";
        }
        String marker = "How to undo:";
        int start = question.indexOf(marker);
        if (start < 0) {
            marker = "Reversal mechanism:";
            start = question.indexOf(marker);
        }
        if (start < 0) {
            return "";
        }
        int end = question.indexOf("Answer only with UI", start);
        if (end < 0) {
            end = question.indexOf("Emphasize how to safely", start);
        }
        if (end < 0) {
            end = question.indexOf("User Query:", start);
        }
        if (end < 0) {
            end = question.length();
        }
        return question.substring(start + marker.length(), end).trim();
    }

    private static String extractZone(String question) {
        Matcher m = ZONE_PATTERN.matcher(question == null ? "" : question);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private static String extractWaveId(String question) {
        Matcher m = WAVE_PATTERN.matcher(question == null ? "" : question);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private static SupportKnowledgeChunk firstSlug(List<SupportKnowledgeChunk> retrieved, String slug) {
        return retrieved.stream().filter(c -> slug.equals(c.slug())).findFirst().orElse(null);
    }

    private static String trimBody(String body) {
        return body == null ? "" : body.strip();
    }
}
