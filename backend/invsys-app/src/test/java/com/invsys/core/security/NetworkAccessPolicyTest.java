package com.invsys.core.security;

import com.invsys.domain.NetworkAccessLevel;
import com.invsys.domain.Role;
import com.invsys.domain.TenantSsoConfig;
import com.invsys.repository.RoleRepository;
import com.invsys.repository.TenantSsoConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkAccessPolicyTest {

    @Mock RoleRepository roleRepository;
    @Mock TenantSsoConfigRepository ssoConfigRepository;

    @Test
    void emptyCidrsDisableFencing() {
        NetworkAccessPolicy policy = new NetworkAccessPolicy(roleRepository, ssoConfigRepository);
        assertThat(policy.evaluate("203.0.113.9", List.of(), NetworkAccessLevel.STRICT_INTERNAL, false))
                .isEqualTo(NetworkAccessPolicy.Decision.ALLOW);
    }

    @Test
    void internalIpAlwaysAllows() {
        NetworkAccessPolicy policy = new NetworkAccessPolicy(roleRepository, ssoConfigRepository);
        assertThat(policy.evaluate(
                "10.1.2.3", List.of("10.0.0.0/8"), NetworkAccessLevel.STRICT_INTERNAL, false))
                .isEqualTo(NetworkAccessPolicy.Decision.ALLOW);
    }

    @Test
    void externalStrictDenies_roamingAllows_mfaRequiresClaim() {
        NetworkAccessPolicy policy = new NetworkAccessPolicy(roleRepository, ssoConfigRepository);
        List<String> cidrs = List.of("10.0.0.0/8");
        assertThat(policy.evaluate("203.0.113.9", cidrs, NetworkAccessLevel.STRICT_INTERNAL, false))
                .isEqualTo(NetworkAccessPolicy.Decision.DENY_STRICT);
        assertThat(policy.evaluate("203.0.113.9", cidrs, NetworkAccessLevel.ROAMING, false))
                .isEqualTo(NetworkAccessPolicy.Decision.ALLOW);
        assertThat(policy.evaluate("203.0.113.9", cidrs, NetworkAccessLevel.MFA_OUTSIDE_NETWORK, false))
                .isEqualTo(NetworkAccessPolicy.Decision.MFA_REQUIRED);
        assertThat(policy.evaluate("203.0.113.9", cidrs, NetworkAccessLevel.MFA_OUTSIDE_NETWORK, true))
                .isEqualTo(NetworkAccessPolicy.Decision.ALLOW);
    }

    @Test
    void highestForRoleCodesUsesRoleRows() {
        UUID tenant = UUID.randomUUID();
        Role picker = new Role();
        picker.setCode("PICKER");
        picker.setNetworkAccessLevel(NetworkAccessLevel.STRICT_INTERNAL);
        Role field = new Role();
        field.setCode("WAREHOUSE_MANAGER");
        field.setNetworkAccessLevel(NetworkAccessLevel.ROAMING);
        when(roleRepository.findByTenantIdAndCode(tenant, "PICKER")).thenReturn(Optional.of(picker));
        when(roleRepository.findByTenantIdAndCode(tenant, "WAREHOUSE_MANAGER")).thenReturn(Optional.of(field));

        NetworkAccessPolicy policy = new NetworkAccessPolicy(roleRepository, ssoConfigRepository);
        assertThat(policy.highestForRoleCodes(tenant, List.of("PICKER", "WAREHOUSE_MANAGER")))
                .isEqualTo(NetworkAccessLevel.ROAMING);
    }

    @Test
    void allowedCidrBlocksReadFromSsoAlias() {
        UUID tenant = UUID.randomUUID();
        TenantSsoConfig config = new TenantSsoConfig();
        config.setAllowedCidrBlocks(List.of("192.168.0.0/16"));
        when(ssoConfigRepository.findByTenantId(tenant)).thenReturn(Optional.of(config));
        NetworkAccessPolicy policy = new NetworkAccessPolicy(roleRepository, ssoConfigRepository);
        assertThat(policy.allowedCidrBlocks(tenant)).containsExactly("192.168.0.0/16");
        assertThat(config.getCorporateCidrIps()).containsExactly("192.168.0.0/16");
    }
}
