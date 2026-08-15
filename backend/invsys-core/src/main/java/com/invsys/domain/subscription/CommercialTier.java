package com.invsys.domain.subscription;

/**
 * Commercial subscription tier for a tenant.
 */
public enum CommercialTier {
    BASIC,
    INTERMEDIATE,
    ENTERPRISE;

    public static CommercialTier fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return BASIC;
        }
        return CommercialTier.valueOf(raw.trim().toUpperCase());
    }
}
