package com.invsys.core.security.dto;

import jakarta.validation.constraints.NotBlank;

public record TerminalBiometricRequest(
        @NotBlank String credentialId,
        @NotBlank String challenge,
        @NotBlank String signature
) {
}
