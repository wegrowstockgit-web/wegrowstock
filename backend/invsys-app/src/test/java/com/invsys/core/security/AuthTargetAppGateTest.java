package com.invsys.core.security;

import com.invsys.core.security.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthTargetAppGateTest {

    @Test
    void posAndWmsGatesUsePermissionAndRoleBoundaries() {
        assertThat(AuthService.hasPosOperate(List.of("RETAIL_CASHIER"), List.of(PermissionKeys.POS_OPERATE))).isTrue();
        assertThat(AuthService.hasPosOperate(List.of("PICKER"), List.of(PermissionKeys.PRINTING_THERMAL))).isFalse();
        assertThat(AuthService.hasPosOperate(List.of("OWNER"), List.of())).isTrue();
        assertThat(AuthService.hasWmsAccess(List.of("RETAIL_CASHIER"), List.of(PermissionKeys.POS_OPERATE))).isFalse();
        assertThat(AuthService.hasWmsAccess(List.of("PICKER"), List.of())).isTrue();
        assertThat(AuthService.isWmsPermission(PermissionKeys.INVENTORY_ADJUST)).isTrue();
        assertThat(AuthService.isWmsPermission(PermissionKeys.POS_SUPERVISE)).isFalse();
    }

    @Test
    void assertTargetAppAccess_rejectsCrossAppLogins() {
        TokenResponse cashier = tokens(List.of("RETAIL_CASHIER"), List.of(PermissionKeys.POS_OPERATE));
        TokenResponse picker = tokens(List.of("PICKER"), List.of(PermissionKeys.PRINTING_THERMAL));
        TokenResponse owner = tokens(List.of("OWNER"), List.copyOf(PermissionKeys.CATALOG));

        assertThatCode(() -> AuthService.assertTargetAppAccess("POS", cashier)).doesNotThrowAnyException();
        assertThatCode(() -> AuthService.assertTargetAppAccess("WMS", picker)).doesNotThrowAnyException();
        assertThatCode(() -> AuthService.assertTargetAppAccess("POS", owner)).doesNotThrowAnyException();
        assertThatCode(() -> AuthService.assertTargetAppAccess(null, picker)).doesNotThrowAnyException();

        assertThatThrownBy(() -> AuthService.assertTargetAppAccess("WMS", cashier))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("WMS access privileges");
        assertThatThrownBy(() -> AuthService.assertTargetAppAccess("POS", picker))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("POS access privileges");
        assertThatThrownBy(() -> AuthService.assertTargetAppAccess("ADMIN", owner))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("admin access privileges");
    }

    @Test
    void normalizeAppContext_keepsPosAndWmsOnly() {
        assertThat(AuthService.normalizeAppContext("pos")).isEqualTo("POS");
        assertThat(AuthService.normalizeAppContext(" WMS ")).isEqualTo("WMS");
        assertThat(AuthService.normalizeAppContext("ADMIN")).isNull();
        assertThat(AuthService.normalizeAppContext("")).isNull();
        assertThat(AuthService.normalizeAppContext(null)).isNull();
    }

    private static TokenResponse tokens(List<String> roles, List<String> permissions) {
        return new TokenResponse(
                "access",
                "refresh",
                UUID.randomUUID(),
                UUID.randomUUID(),
                roles,
                List.of(),
                null,
                permissions);
    }
}
