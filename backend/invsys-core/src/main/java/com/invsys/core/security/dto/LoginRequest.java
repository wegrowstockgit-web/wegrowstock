package com.invsys.core.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Slugless login — tenant resolved from globally unique email. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String targetApp,
        String mfaCredentialId,
        String mfaChallenge,
        String mfaSignature
) {
    public LoginRequest(String email, String password) {
        this(email, password, null, null, null, null);
    }

    public LoginRequest(String email, String password, String targetApp) {
        this(email, password, targetApp, null, null, null);
    }

    public boolean hasMfaAssertion() {
        return mfaCredentialId != null && !mfaCredentialId.isBlank()
                && mfaChallenge != null && !mfaChallenge.isBlank()
                && mfaSignature != null && !mfaSignature.isBlank();
    }
}
