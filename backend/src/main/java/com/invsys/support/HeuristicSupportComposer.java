package com.invsys.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        if (manager && (q.contains("damaged") || q.contains("exception") || q.contains("damage"))) {
            SupportKnowledgeChunk doc = firstSlug(retrieved, "manager-damaged-exception");
            if (doc != null) {
                return HeuristicSupportResult.of(trimBody(doc.body()));
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
