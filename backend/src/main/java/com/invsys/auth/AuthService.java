package com.invsys.auth;

import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.RefreshRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.common.ApiException;
import com.invsys.config.JwtProperties;
import com.invsys.domain.RefreshToken;
import com.invsys.domain.User;
import com.invsys.repository.RefreshTokenRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.service.TenantOnboardingService;
import com.invsys.tenancy.BootstrapJdbc;
import com.invsys.tenancy.TenantContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final TenantOnboardingService onboardingService;
    private final BootstrapJdbc bootstrapJdbc;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final AuthService self;

    public AuthService(TenantOnboardingService onboardingService,
                       BootstrapJdbc bootstrapJdbc,
                       TenantRepository tenantRepository,
                       UserRepository userRepository,
                       UserRoleRepository userRoleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder,
                       @Lazy AuthService self) {
        this.onboardingService = onboardingService;
        this.bootstrapJdbc = bootstrapJdbc;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.self = self;
    }

    public TokenResponse signup(SignupRequest request) {
        TenantOnboardingService.OnboardingResult result = onboardingService.signup(request);
        return issueTokens(result.user(), result.roles());
    }

    public TokenResponse login(LoginRequest request) {
        var authUser = bootstrapJdbc.findUserForAuthByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials"));

        UUID tenantId = authUser.tenantId();

        bootstrapJdbc.findSsoConfigByTenantId(tenantId).ifPresent(sso -> {
            if (sso.enabled() && sso.forceSso()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "SSO_REQUIRED",
                        "Corporate SSO is required for this tenant")
                        .withProperty("ssoAuthorizationUrl", "/oauth2/authorization/" + tenantId);
            }
        });

        if (!"ACTIVE".equals(authUser.status())
                || !passwordEncoder.matches(request.password(), authUser.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials");
        }
        TenantContext.setTenantId(tenantId);
        try {
            return self.completeLogin(authUser.id());
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public TokenResponse completeLogin(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials"));
        List<String> roles = userRoleRepository.findRoleCodesByUserId(userId);
        return issueTokens(user, roles);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = hashToken(request.refreshToken());
        var stored = bootstrapJdbc.findRefreshTokenByHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid refresh token"));
        if (stored.revokedAt() != null || stored.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid refresh token");
        }
        TenantContext.setTenantId(stored.tenantId());
        TenantContext.setUserId(stored.userId());
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid refresh token"));
        User user = userRepository.findById(stored.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid refresh token"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_INACTIVE", "User is inactive");
        }
        tokenEntity.setRevokedAt(Instant.now());
        refreshTokenRepository.save(tokenEntity);

        List<String> roles = userRoleRepository.findRoleCodesByUserId(user.getId());
        List<UUID> warehouseIds = resolveWarehouseIds(user.getTenantId(), user.getId(), roles);
        String access = jwtService.generateAccessToken(user.getId(), user.getTenantId(), roles, warehouseIds);
        String refresh = UUID.randomUUID().toString();
        RefreshToken replacement = new RefreshToken();
        replacement.setTenantId(user.getTenantId());
        replacement.setUserId(user.getId());
        replacement.setTokenHash(hashToken(refresh));
        replacement.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenDays() * 86400L));
        replacement = refreshTokenRepository.save(replacement);
        tokenEntity.setReplacedBy(replacement.getId());
        refreshTokenRepository.save(tokenEntity);
        return new TokenResponse(access, refresh, user.getTenantId(), user.getId(), roles, warehouseIds);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hashToken(refreshToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    private TokenResponse issueTokens(User user, List<String> roles) {
        List<UUID> warehouseIds = resolveWarehouseIds(user.getTenantId(), user.getId(), roles);
        String access = jwtService.generateAccessToken(user.getId(), user.getTenantId(), roles, warehouseIds);
        String refresh = UUID.randomUUID().toString();
        RefreshToken entity = new RefreshToken();
        entity.setTenantId(user.getTenantId());
        entity.setUserId(user.getId());
        entity.setTokenHash(hashToken(refresh));
        entity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenDays() * 86400L));
        refreshTokenRepository.save(entity);
        return new TokenResponse(access, refresh, user.getTenantId(), user.getId(), roles, warehouseIds);
    }

    /**
     * OWNER/ADMIN see all tenant warehouses. Localized roles are restricted to user_warehouses mappings.
     */
    List<UUID> resolveWarehouseIds(UUID tenantId, UUID userId, List<String> roles) {
        if (roles.contains("OWNER") || roles.contains("ADMIN")) {
            return bootstrapJdbc.findAllWarehouseIds(tenantId);
        }
        return bootstrapJdbc.findWarehouseIdsForUser(tenantId, userId);
    }

    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
