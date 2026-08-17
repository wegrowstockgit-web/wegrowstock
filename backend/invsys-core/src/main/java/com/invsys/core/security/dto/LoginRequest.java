package com.invsys.core.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Slugless login — tenant resolved from globally unique email. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String targetApp
) {
    public LoginRequest(String email, String password) {
        this(email, password, null);
    }
}
