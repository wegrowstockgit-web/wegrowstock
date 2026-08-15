package com.invsys.admin.api;

import com.invsys.admin.security.AdminCookieService;
import com.invsys.admin.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/auth")
public class ControlPlaneAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminCookieService adminCookieService;

    public ControlPlaneAuthController(AdminAuthService adminAuthService,
                                      AdminCookieService adminCookieService) {
        this.adminAuthService = adminAuthService;
        this.adminCookieService = adminCookieService;
    }

    @PostMapping("/login")
    public AdminAuthService.AdminMeResponse login(@Valid @RequestBody AdminAuthService.AdminLoginRequest request,
                                                  HttpServletResponse response) {
        AdminAuthService.AdminSession session = adminAuthService.login(request);
        adminCookieService.writeSessionCookies(response, session.accessToken(), session.refreshToken());
        return new AdminAuthService.AdminMeResponse(session.email(), true);
    }

    /** Ensures the browser receives the {@code XSRF-TOKEN} cookie for SPA mutations. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        adminAuthService.logout(adminCookieService.readRefreshToken(request));
        adminCookieService.clearSessionCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AdminAuthService.AdminMeResponse me(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return adminAuthService.currentUser(userId);
    }
}
