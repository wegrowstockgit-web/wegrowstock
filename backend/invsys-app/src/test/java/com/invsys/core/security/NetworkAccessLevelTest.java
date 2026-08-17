package com.invsys.core.security;

import com.invsys.domain.NetworkAccessLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkAccessLevelTest {

    @Test
    void roamingOverridesStrictInternal() {
        assertThat(NetworkAccessLevel.highest(List.of(
                NetworkAccessLevel.STRICT_INTERNAL,
                NetworkAccessLevel.ROAMING,
                NetworkAccessLevel.MFA_OUTSIDE_NETWORK
        ))).isEqualTo(NetworkAccessLevel.ROAMING);
    }

    @Test
    void defaultForRoleSeedsOfficeAsMfaAndFloorAsStrict() {
        assertThat(NetworkAccessLevel.defaultForRole("OWNER")).isEqualTo(NetworkAccessLevel.MFA_OUTSIDE_NETWORK);
        assertThat(NetworkAccessLevel.defaultForRole("PICKER")).isEqualTo(NetworkAccessLevel.STRICT_INTERNAL);
        assertThat(NetworkAccessLevel.defaultForRole("RETAIL_CASHIER")).isEqualTo(NetworkAccessLevel.STRICT_INTERNAL);
        assertThat(NetworkAccessLevel.defaultForRole("B2B_CUSTOMER")).isEqualTo(NetworkAccessLevel.ROAMING);
    }

    @Test
    void fromCodeFallsBackToStrict() {
        assertThat(NetworkAccessLevel.fromCode("roaming")).isEqualTo(NetworkAccessLevel.ROAMING);
        assertThat(NetworkAccessLevel.fromCode("nope")).isEqualTo(NetworkAccessLevel.STRICT_INTERNAL);
        assertThat(NetworkAccessLevel.fromCode(null)).isEqualTo(NetworkAccessLevel.STRICT_INTERNAL);
    }
}
