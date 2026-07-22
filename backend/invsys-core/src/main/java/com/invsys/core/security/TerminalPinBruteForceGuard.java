package com.invsys.core.security;

import org.springframework.stereotype.Component;

import java.util.UUID;
import com.invsys.domain.Tenant;

/**
 * Facade over {@link RedisPinLockoutService} for shared-terminal PIN guesses.
 * Station policy: 3 failures / 5 minutes → 15-minute lockout (unlockAt in problem+json).
 * Tenant spray: 25 failures / same windows.
 */
@Component
public class TerminalPinBruteForceGuard {

    public static final int MAX_FAILURES = RedisPinLockoutService.MAX_FAILURES;
    public static final int MAX_TENANT_FAILURES = 25;

    private final RedisPinLockoutService lockoutService;

    public TerminalPinBruteForceGuard(RedisPinLockoutService lockoutService) {
        this.lockoutService = lockoutService;
    }

    public void assertAllowed(UUID tenantId, UUID stationUserId) {
        lockoutService.assertAllowed(stationKey(tenantId, stationUserId), MAX_FAILURES);
        lockoutService.assertAllowed(tenantKey(tenantId), MAX_TENANT_FAILURES);
    }

    public void recordFailure(UUID tenantId, UUID stationUserId) {
        RuntimeException primary = null;
        try {
            lockoutService.recordFailure(stationKey(tenantId, stationUserId), MAX_FAILURES);
        } catch (RuntimeException ex) {
            primary = ex;
        }
        try {
            lockoutService.recordFailure(tenantKey(tenantId), MAX_TENANT_FAILURES);
        } catch (RuntimeException ex) {
            if (primary == null) {
                primary = ex;
            }
        }
        if (primary != null) {
            throw primary;
        }
    }

    public void recordSuccess(UUID tenantId, UUID stationUserId) {
        lockoutService.recordSuccess(stationKey(tenantId, stationUserId));
    }

    public void assertCredentialAllowed(String emailOrDevice) {
        lockoutService.assertAllowed(credentialKey(emailOrDevice), MAX_FAILURES);
    }

    public void recordCredentialFailure(String emailOrDevice) {
        lockoutService.recordFailure(credentialKey(emailOrDevice), MAX_FAILURES);
    }

    public void recordCredentialSuccess(String emailOrDevice) {
        lockoutService.recordSuccess(credentialKey(emailOrDevice));
    }

    public void reset() {
        lockoutService.reset();
    }

    private static String stationKey(UUID tenantId, UUID stationUserId) {
        return "station:" + tenantId + ":" + (stationUserId != null ? stationUserId : "anon");
    }

    private static String tenantKey(UUID tenantId) {
        return "tenant:" + tenantId;
    }

    private static String credentialKey(String emailOrDevice) {
        return "cred:" + emailOrDevice.trim().toLowerCase();
    }
}
