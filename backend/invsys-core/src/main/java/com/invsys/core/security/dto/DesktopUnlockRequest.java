package com.invsys.core.security.dto;

public record DesktopUnlockRequest(
        String password,
        String mfaCredentialId,
        String mfaChallenge,
        String mfaSignature
) {
}
