package com.invsys.chatbot.service;

import java.util.List;

/**
 * Structured attributes stamped onto each child chunk during hierarchical ingestion.
 */
public record ChunkMetadataExtraction(
        String module,
        List<String> targetRoles,
        String errorCode,
        String resolutionLevel,
        List<String> entitiesMentioned
) {
    public static ChunkMetadataExtraction empty() {
        return new ChunkMetadataExtraction("NONE", List.of(), "NONE", "OPERATOR_SELF_SERVICE", List.of());
    }
}
