package com.invsys.core.security;

import com.invsys.domain.NetworkAccessLevel;
import com.invsys.domain.Role;
import com.invsys.domain.TenantSsoConfig;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.TenantSsoConfigRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Conditional-access decision engine: role network level × client IP × MFA claim.
 * An empty CIDR allowlist means fencing is not configured (allow).
 */
@Component
public class NetworkAccessPolicy {

    public enum Decision {
        ALLOW,
        DENY_STRICT,
        MFA_REQUIRED
    }

    public static final String STRICT_DENIED_DETAIL =
            "Access denied. Role requires internal corporate network connection.";
    public static final String MFA_REQUIRED_CODE = "MFA_REQUIRED_FOR_EXTERNAL_ACCESS";

    private final RoleRepository roleRepository;
    private final TenantSsoConfigRepository ssoConfigRepository;

    public NetworkAccessPolicy(RoleRepository roleRepository,
                               TenantSsoConfigRepository ssoConfigRepository) {
        this.roleRepository = roleRepository;
        this.ssoConfigRepository = ssoConfigRepository;
    }

    public NetworkAccessLevel highestForRoleCodes(UUID tenantId, Collection<String> roleCodes) {
        if (tenantId == null || roleCodes == null || roleCodes.isEmpty()) {
            return NetworkAccessLevel.STRICT_INTERNAL;
        }
        List<NetworkAccessLevel> levels = new ArrayList<>();
        for (String code : roleCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            roleRepository.findByTenantIdAndCode(tenantId, code.trim())
                    .map(Role::getNetworkAccessLevel)
                    .ifPresent(levels::add);
        }
        return NetworkAccessLevel.highest(levels);
    }

    public List<String> allowedCidrBlocks(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return ssoConfigRepository.findByTenantId(tenantId)
                .map(TenantSsoConfig::getAllowedCidrBlocks)
                .orElse(List.of());
    }

    public Decision evaluate(String clientIp,
                             List<String> cidrs,
                             NetworkAccessLevel level,
                             boolean mfaVerified) {
        return evaluate(clientIp, cidrs, level, mfaVerified, false);
    }

    public Decision evaluate(String clientIp,
                             List<String> cidrs,
                             NetworkAccessLevel level,
                             boolean mfaVerified,
                             boolean treatAsInternal) {
        if (cidrs == null || cidrs.isEmpty()) {
            return Decision.ALLOW;
        }
        NetworkAccessLevel effective = level == null ? NetworkAccessLevel.STRICT_INTERNAL : level;
        boolean internal = treatAsInternal
                || ClientIpResolver.isLoopback(clientIp)
                || CorporateCidrMatcher.matches(clientIp, cidrs);
        if (internal) {
            return Decision.ALLOW;
        }
        return switch (effective) {
            case ROAMING -> Decision.ALLOW;
            case STRICT_INTERNAL -> Decision.DENY_STRICT;
            case MFA_OUTSIDE_NETWORK -> mfaVerified ? Decision.ALLOW : Decision.MFA_REQUIRED;
        };
    }
}
