package com.invsys.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserControllerRoleCodesTest {

    @Test
    void resolveRoleCodesMergesLegacyAndArrayFields() {
        assertThat(UserController.resolveRoleCodes("VIEWER", List.of("PICKER"), List.of("ADMIN", "picker")))
                .containsExactly("VIEWER", "PICKER", "ADMIN");
        assertThat(UserController.resolveRoleCodes(null, null, null)).isEmpty();
        assertThat(UserController.resolveRoleCodes("  ", List.of(""), List.of())).isEmpty();
    }
}
