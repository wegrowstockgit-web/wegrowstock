package com.invsys.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MagicLoginConsumeRequest(
        @NotBlank String token
) {
}
