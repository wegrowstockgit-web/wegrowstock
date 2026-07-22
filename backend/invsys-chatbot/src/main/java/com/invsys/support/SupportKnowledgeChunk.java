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
        double score
) {
}
