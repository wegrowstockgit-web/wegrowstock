package com.invsys.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MistakeFixDto(
        @NotBlank @Size(max = 500) String mistake,
        @NotBlank @Size(max = 2000) String solution,
        @NotBlank @Size(max = 80) String requiredRole
) {
}
