package com.invsys.core.security.dto;

import jakarta.validation.constraints.NotBlank;

public record MagicLoginConsumeRequest(
        @NotBlank String token
) {
}
