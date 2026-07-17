package com.invsys;

import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.RefreshRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthFlowTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;

    @Test
    void signupLoginRefreshLogoutFlow() {
        String stamp = String.valueOf(System.nanoTime());
        String email = "owner.flow." + stamp + "@example.test";
        SignupRequest signup = new SignupRequest(
                "Flow Co " + stamp, "flow-" + stamp, email, "password123", "Owner User");
        TokenResponse created = authService.signup(signup);
        assertThat(created.accessToken()).isNotBlank();
        assertThat(created.roles()).contains("OWNER");

        TokenResponse loggedIn = authService.login(new LoginRequest(email, "password123"));
        assertThat(loggedIn.tenantId()).isEqualTo(created.tenantId());

        TokenResponse refreshed = authService.refresh(new RefreshRequest(loggedIn.refreshToken()));
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();

        authService.logout(refreshed.refreshToken());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(refreshed.refreshToken())))
                .isInstanceOf(Exception.class);
    }
}
