package com.invsys.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Slugless login — tenant resolved from globally unique email. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
