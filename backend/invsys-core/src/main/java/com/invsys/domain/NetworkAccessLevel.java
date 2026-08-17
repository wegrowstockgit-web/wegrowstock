package com.invsys.domain;

/**
 * Conditional-access fence for a role. Highest assigned level wins
 * ({@link #ROAMING} overrides {@link #STRICT_INTERNAL}).
 */
public enum NetworkAccessLevel {
    /** Cashiers / pickers — warehouse LAN only. */
    STRICT_INTERNAL,
    /** Office staff — MFA required when off the corporate CIDR. */
    MFA_OUTSIDE_NETWORK,
    /** Field technicians — fully roaming. */
    ROAMING;

    public int rank() {
        return switch (this) {
            case STRICT_INTERNAL -> 0;
            case MFA_OUTSIDE_NETWORK -> 1;
            case ROAMING -> 2;
        };
    }

    public static NetworkAccessLevel highest(Iterable<NetworkAccessLevel> levels) {
        NetworkAccessLevel best = STRICT_INTERNAL;
        if (levels == null) {
            return best;
        }
        for (NetworkAccessLevel level : levels) {
            if (level != null && level.rank() > best.rank()) {
                best = level;
            }
        }
        return best;
    }

    public static NetworkAccessLevel fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return STRICT_INTERNAL;
        }
        try {
            return NetworkAccessLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return STRICT_INTERNAL;
        }
    }

    public static NetworkAccessLevel defaultForRole(String roleCode) {
        if (roleCode == null) {
            return STRICT_INTERNAL;
        }
        return switch (roleCode.trim().toUpperCase()) {
            case "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "VIEWER", "RETAIL_MANAGER" -> MFA_OUTSIDE_NETWORK;
            case "B2B_CUSTOMER", "SUPPLIER" -> ROAMING;
            default -> STRICT_INTERNAL;
        };
    }
}
