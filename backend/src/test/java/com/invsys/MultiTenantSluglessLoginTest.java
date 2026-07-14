package com.invsys;

import com.invsys.auth.AuthService;
import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seed-independent coverage for slugless multi-tenant login and global email unicity.
 */
class MultiTenantSluglessLoginTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;

    @Test
    void twoTenantsLoginByEmailOnlyAndStayIsolated() {
        String slugA = "mt-a-" + UUID.randomUUID().toString().substring(0, 8);
        String slugB = "mt-b-" + UUID.randomUUID().toString().substring(0, 8);

        TokenResponse a = authService.signup(new SignupRequest(
                "MT Co A", slugA, "owner@" + slugA + ".test", "password123", "Owner A"));
        TokenResponse b = authService.signup(new SignupRequest(
                "MT Co B", slugB, "owner@" + slugB + ".test", "password123", "Owner B"));

        TokenResponse loginA = authService.login(new LoginRequest("owner@" + slugA + ".test", "password123"));
        TokenResponse loginB = authService.login(new LoginRequest("owner@" + slugB + ".test", "password123"));

        assertThat(loginA.tenantId()).isEqualTo(a.tenantId());
        assertThat(loginB.tenantId()).isEqualTo(b.tenantId());
        assertThat(loginA.tenantId()).isNotEqualTo(loginB.tenantId());
        assertThat(loginA.roles()).contains("OWNER");
        assertThat(loginB.warehouseIds()).isNotNull();
    }

    @Test
    void duplicateEmailAcrossTenantsIsRejectedOnSignup() {
        String slug = "mt-dup-" + UUID.randomUUID().toString().substring(0, 8);
        authService.signup(new SignupRequest(
                "First Co", slug, "shared@" + slug + ".test", "password123", "Owner"));

        String slug2 = "mt-dup2-" + UUID.randomUUID().toString().substring(0, 8);
        assertThatThrownBy(() -> authService.signup(new SignupRequest(
                        "Second Co", slug2, "shared@" + slug + ".test", "password123", "Other")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus().value())
                        .isIn(HttpStatus.CONFLICT.value(), HttpStatus.BAD_REQUEST.value(),
                                HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
}
