package com.invsys.auth;

import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.MagicLoginConsumeRequest;
import com.invsys.auth.dto.MagicLoginRequest;
import com.invsys.auth.dto.MeResponse;
import com.invsys.auth.dto.RefreshRequest;
import com.invsys.auth.dto.SessionResponse;
import com.invsys.auth.dto.SetTerminalPinRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TerminalBiometricRequest;
import com.invsys.auth.dto.TerminalSwitchRequest;
import com.invsys.auth.dto.TerminalSwitchResponse;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.auth.dto.WarehouseLoginRequest;
import com.invsys.common.ApiException;
import com.invsys.service.TerminalBiometricService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final MagicLoginService magicLoginService;
    private final TerminalBiometricService terminalBiometricService;
    private final TenantSsoResolver tenantSsoResolver;
    private final SsoProviderCatalog ssoProviderCatalog;
    private final AuthCookieService authCookieService;
    private final boolean publicSignupEnabled;

    public AuthController(AuthService authService,
                          MagicLoginService magicLoginService,
                          TerminalBiometricService terminalBiometricService,
                          TenantSsoResolver tenantSsoResolver,
                          SsoProviderCatalog ssoProviderCatalog,
                          AuthCookieService authCookieService,
                          @Value("${invsys.security.public-signup-enabled:true}") boolean publicSignupEnabled) {
        this.authService = authService;
        this.magicLoginService = magicLoginService;
        this.terminalBiometricService = terminalBiometricService;
        this.tenantSsoResolver = tenantSsoResolver;
        this.ssoProviderCatalog = ssoProviderCatalog;
        this.authCookieService = authCookieService;
        this.publicSignupEnabled = publicSignupEnabled;
    }

    @PostMapping("/signup")
    public SessionResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        if (!publicSignupEnabled) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SIGNUP_DISABLED", "Public signup is disabled");
        }
        return issueSession(authService.signup(request), response);
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return issueSession(authService.login(request), response);
    }

    /**
     * Surface B: email + PIN warehouse login. Issues HttpOnly session cookies.
     */
    @PostMapping("/warehouse/login")
    public SessionResponse warehouseLogin(@Valid @RequestBody WarehouseLoginRequest request,
                                          HttpServletResponse response) {
        return issueSession(authService.warehouseLogin(request), response);
    }

    @GetMapping("/sso-discover")
    public Map<String, Object> ssoDiscover(@RequestParam String email) {
        return tenantSsoResolver.resolveByEmail(email)
                .map(route -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("ssoRequired", true);
                    body.put("protocol", route.protocol());
                    body.put("authorizationUrl", route.authorizationUrl());
                    body.put("forceSso", route.forceSso());
                    body.put("provider", ssoProviderCatalog.inferProvider(route.issuerUrl()));
                    body.put("issuerUrl", route.issuerUrl());
                    return body;
                })
                .orElseGet(() -> Map.of("ssoRequired", false));
    }

    /** Public IdP presets for login buttons (Google / Entra / Okta). */
    @GetMapping("/sso-providers")
    public Map<String, Object> ssoProviders() {
        return Map.of("providers", ssoProviderCatalog.presets());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public MeResponse me() {
        return authService.currentUser();
    }

    @PostMapping("/magic-login")
    public Map<String, Object> requestMagicLogin(@Valid @RequestBody MagicLoginRequest request) {
        return magicLoginService.requestMagicLink(request.email());
    }

    @PostMapping("/magic-login/consume")
    public SessionResponse consumeMagicLogin(@Valid @RequestBody MagicLoginConsumeRequest request,
                                             HttpServletResponse response) {
        return issueSession(magicLoginService.consumeMagicLink(request.token()), response);
    }

    @PostMapping("/terminal-switch")
    @PreAuthorize("isAuthenticated()")
    public TerminalSwitchResponse terminalSwitch(@Valid @RequestBody TerminalSwitchRequest request,
                                                 HttpServletResponse response) {
        TerminalSwitchResponse switched = authService.terminalSwitch(request);
        authCookieService.writeTerminalAccessCookie(response, switched.accessToken(), switched.expiresInSeconds());
        return switched.withoutAccessToken();
    }

    @PostMapping("/terminal-biometric/options")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> terminalBiometricOptions() {
        return terminalBiometricService.createAssertionOptions();
    }

    @PostMapping("/terminal-biometric")
    @PreAuthorize("isAuthenticated()")
    public TerminalSwitchResponse terminalBiometric(@Valid @RequestBody TerminalBiometricRequest request,
                                                    HttpServletResponse response) {
        TerminalSwitchResponse switched = terminalBiometricService.assertTerminal(
                request.credentialId(), request.challenge(), request.signature());
        authCookieService.writeTerminalAccessCookie(response, switched.accessToken(), switched.expiresInSeconds());
        return switched.withoutAccessToken();
    }

    @PostMapping("/terminal-pin")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> setOwnTerminalPin(@Valid @RequestBody SetTerminalPinRequest request) {
        authService.setOwnTerminalPin(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(HttpServletRequest request,
                                   HttpServletResponse response,
                                   @RequestBody(required = false) RefreshRequest body) {
        String refreshToken = authCookieService.readRefreshToken(request);
        if ((refreshToken == null || refreshToken.isBlank()) && body != null) {
            refreshToken = body.refreshToken();
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Missing refresh token");
        }
        return issueSession(authService.refresh(new RefreshRequest(refreshToken)), response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService.readRefreshToken(request);
        authService.logout(refreshToken);
        authCookieService.clearSessionCookies(response);
        return ResponseEntity.noContent().build();
    }

    private SessionResponse issueSession(TokenResponse tokens, HttpServletResponse response) {
        authCookieService.writeSessionCookies(response, tokens);
        return SessionResponse.from(tokens);
    }
}
