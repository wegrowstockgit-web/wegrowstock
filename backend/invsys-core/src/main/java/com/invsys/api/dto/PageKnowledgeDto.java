package com.invsys.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PageKnowledgeDto(
        UUID id,
        String routePattern,
        String category,
        String title,
        String summary,
        String rolePrivileges,
        List<String> keyActions,
        List<MistakeFixDto> commonMistakes,
        String proTip,
        Instant updatedAt
) {
}
