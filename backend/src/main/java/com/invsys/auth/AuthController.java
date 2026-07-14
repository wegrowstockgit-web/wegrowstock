package com.invsys.auth;

import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.MagicLoginConsumeRequest;
import com.invsys.auth.dto.MagicLoginRequest;
import com.invsys.auth.dto.RefreshRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final MagicLoginService magicLoginService;
    private final boolean publicSignupEnabled;

    public AuthController(AuthService authService,
                          MagicLoginService magicLoginService,
                          @Value("${invsys.security.public-signup-enabled:true}") boolean publicSignupEnabled) {
        this.authService = authService;
        this.magicLoginService = magicLoginService;
        this.publicSignupEnabled = publicSignupEnabled;
    }

    @PostMapping("/signup")
    public TokenResponse signup(@Valid @RequestBody SignupRequest request) {
        if (!publicSignupEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SIGNUP_DISABLED", "Public signup is disabled");
        }
        return authService.signup(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/magic-login")
    public Map<String, Object> requestMagicLogin(@Valid @RequestBody MagicLoginRequest request) {
        return magicLoginService.requestMagicLink(request.email());
    }

    @PostMapping("/magic-login/consume")
    public TokenResponse consumeMagicLogin(@Valid @RequestBody MagicLoginConsumeRequest request) {
        return magicLoginService.consumeMagicLink(request.token());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-Refresh-Token", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }
}
