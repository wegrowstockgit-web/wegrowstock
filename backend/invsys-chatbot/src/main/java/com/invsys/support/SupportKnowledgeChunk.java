package com.invsys.support;

import java.util.List;
import java.util.UUID;

public record SupportKnowledgeChunk(
        UUID id,
        String slug,
        String title,
        String body,
        List<String> audienceRoles,
        List<String> routeHints,
        String sourcePath,
        double score,
        UUID parentChunkId,
        String parentContent,
        String contextSummary,
        String enrichedMetadataJson
) {
    /** Legacy constructor used by seeds/tests that predate hierarchical RAG. */
    public SupportKnowledgeChunk(
            UUID id,
            String slug,
            String title,
            String body,
            List<String> audienceRoles,
            List<String> routeHints,
            String sourcePath,
            double score
    ) {
        this(id, slug, title, body, audienceRoles, routeHints, sourcePath, score, null, null, null, null);
    }

    /**
     * Prefer full parent context for generation; fall back to the matched child body.
     */
    public String promptBody() {
        if (parentContent != null && !parentContent.isBlank()) {
            return parentContent;
        }
        return body == null ? "" : body;
    }
}
