package com.invsys.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PageKnowledgeWriteRequest(
        @NotBlank @Size(max = 255) String routePattern,
        @NotBlank @Size(max = 100) String category,
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 8000) String summary,
        @NotBlank @Size(max = 4000) String rolePrivileges,
        @NotNull List<@NotBlank @Size(max = 400) String> keyActions,
        @NotNull List<@Valid MistakeFixDto> commonMistakes,
        @Size(max = 2000) String proTip
) {
}
