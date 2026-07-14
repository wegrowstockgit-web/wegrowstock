package com.invsys;

import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.RefreshRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.domain.RefreshToken;
import com.invsys.repository.RefreshTokenRepository;
import com.invsys.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFlowTest extends AbstractIntegrationTest {

    @Autowired AuthService authService;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @Test
    void signupLoginRefreshLogoutFlow() {
        SignupRequest signup = new SignupRequest("Acme Inc", "acme-test", "owner@acme.test",
                "password123", "Owner User");
        TokenResponse created = authService.signup(signup);
        assertThat(created.accessToken()).isNotBlank();
        assertThat(created.roles()).contains("OWNER");

        TokenResponse loggedIn = authService.login(new LoginRequest("owner@acme.test", "password123"));
        assertThat(loggedIn.tenantId()).isEqualTo(created.tenantId());

        TokenResponse refreshed = authService.refresh(new RefreshRequest(loggedIn.refreshToken()));
        assertThat(refreshed.accessToken()).isNotBlank();

        long activeBefore = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getRevokedAt() == null).count();
        authService.logout(refreshed.refreshToken());
        long activeAfter = refreshTokenRepository.findAll().stream()
                .filter(t -> t.getRevokedAt() == null).count();
        assertThat(activeAfter).isLessThan(activeBefore);
    }
}
