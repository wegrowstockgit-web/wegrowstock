package com.invsys;

import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.RefreshRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.security.AuthService;
import com.invsys.core.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthFlowTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;
    @Autowired JwtService jwtService;

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
        assertThat(jwtService.extractAppContext(loggedIn.accessToken())).isNull();

        TokenResponse posSession = authService.login(new LoginRequest(email, "password123", "POS"));
        assertThat(jwtService.extractAppContext(posSession.accessToken())).isEqualTo("POS");

        TokenResponse refreshed = authService.refresh(new RefreshRequest(loggedIn.refreshToken()));
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotBlank();
        assertThat(jwtService.extractAppContext(refreshed.accessToken())).isNull();

        TokenResponse posRefreshed = authService.refresh(new RefreshRequest(posSession.refreshToken()));
        assertThat(jwtService.extractAppContext(posRefreshed.accessToken())).isEqualTo("POS");

        authService.logout(refreshed.refreshToken());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(refreshed.refreshToken())))
                .isInstanceOf(Exception.class);
    }
}
