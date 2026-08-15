package com.invsys.admin.service;

import com.invsys.config.JwtProperties;
import com.invsys.core.common.ApiException;
import com.invsys.core.security.JwtService;
import com.invsys.domain.PlatformAdmin;
import com.invsys.domain.PlatformAdminRefreshToken;
import com.invsys.repository.PlatformAdminRefreshTokenRepository;
import com.invsys.repository.PlatformAdminRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AdminAuthService {

    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAdminRefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(PlatformAdminRepository platformAdminRepository,
                            PlatformAdminRefreshTokenRepository refreshTokenRepository,
                            JwtService jwtService,
                            JwtProperties jwtProperties,
                            PasswordEncoder passwordEncoder) {
        this.platformAdminRepository = platformAdminRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AdminSession login(AdminLoginRequest request) {
        PlatformAdmin admin = platformAdminRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials"));

        if (!admin.isActive() || !passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }
        return issueSession(admin);
    }

    @Transactional(readOnly = true)
    public AdminMeResponse currentUser(UUID adminId) {
        PlatformAdmin admin = platformAdminRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        if (!admin.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_SUPER_ADMIN", "Super admin access required");
        }
        return new AdminMeResponse(admin.getEmail(), true);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String hash = hashToken(refreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public AdminSession issueSession(PlatformAdmin admin) {
        String access = jwtService.generateAdminAccessToken(admin.getId());
        String refresh = UUID.randomUUID().toString();
        PlatformAdminRefreshToken entity = new PlatformAdminRefreshToken();
        entity.setAdminId(admin.getId());
        entity.setTokenHash(hashToken(refresh));
        entity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenDays() * 86400L));
        refreshTokenRepository.save(entity);
        return new AdminSession(access, refresh, admin.getEmail());
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash token", e);
        }
    }

    public record AdminLoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record AdminSession(String accessToken, String refreshToken, String email) {
    }

    public record AdminMeResponse(String email, boolean superAdmin) {
    }
}
