package com.invsys.auth;

import com.invsys.auth.dto.LoginRequest;
import com.invsys.auth.dto.RefreshRequest;
import com.invsys.auth.dto.SignupRequest;
import com.invsys.auth.dto.SetTerminalPinRequest;
import com.invsys.auth.dto.TerminalSwitchRequest;
import com.invsys.auth.dto.TerminalSwitchResponse;
import com.invsys.auth.dto.MeResponse;
import com.invsys.auth.dto.TokenResponse;
import com.invsys.common.ApiException;
import com.invsys.config.JwtProperties;
import com.invsys.domain.RefreshToken;
import com.invsys.domain.User;
import com.invsys.media.MediaUrlValidator;
import com.invsys.repository.RefreshTokenRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.repository.VehicleAssignmentRepository;
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
import java.util.ArrayList;
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
    private final VehicleAssignmentRepository vehicleAssignmentRepository;
    private final MediaUrlValidator mediaUrlValidator;
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
                       VehicleAssignmentRepository vehicleAssignmentRepository,
                       MediaUrlValidator mediaUrlValidator,
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
        this.vehicleAssignmentRepository = vehicleAssignmentRepository;
        this.mediaUrlValidator = mediaUrlValidator;
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
                String protocol = sso.protocol() != null ? sso.protocol() : "OIDC";
                String ssoUrl = "SAML".equalsIgnoreCase(protocol)
                        ? "/saml2/authenticate/" + tenantId
                        : "/oauth2/authorization/" + tenantId;
                throw new ApiException(HttpStatus.FORBIDDEN, "SSO_REQUIRED",
                        "Corporate SSO is required for this tenant")
                        .withProperty("ssoAuthorizationUrl", ssoUrl);
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
        return new TokenResponse(access, refresh, user.getTenantId(), user.getId(), roles, warehouseIds,
                user.getAvatarUrl());
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

    /**
     * Shared-terminal PIN pad: swap operator JWT context without issuing a new refresh token
     * (primary device session remains intact on the client).
     */
    @Transactional(readOnly = true)
    public TerminalSwitchResponse terminalSwitch(TerminalSwitchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID switchedFrom = TenantContext.getUserId().orElse(null);
        String pinHash = hashTerminalPin(tenantId, request.pin());
        User target = userRepository.findByTenantIdAndTerminalPinHash(tenantId, pinHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PIN",
                        "Invalid terminal PIN"));
        if (!"ACTIVE".equals(target.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "USER_INACTIVE", "User is inactive");
        }
        List<String> roles = userRoleRepository.findRoleCodesByUserId(target.getId());
        List<UUID> warehouseIds = resolveWarehouseIds(tenantId, target.getId(), roles);
        return buildTerminalSwitchResponse(target, roles, warehouseIds, switchedFrom);
    }

    public String issueTerminalAccessToken(User user, List<String> roles, List<UUID> warehouseIds) {
        return jwtService.generateTerminalSwitchToken(user.getId(), user.getTenantId(), roles, warehouseIds);
    }

    TerminalSwitchResponse buildTerminalSwitchResponse(User target,
                                                       List<String> roles,
                                                       List<UUID> warehouseIds,
                                                       UUID switchedFrom) {
        int ttlMinutes = jwtProperties.getTerminalSwitchTokenMinutes();
        String access = jwtService.generateTerminalSwitchToken(
                target.getId(), target.getTenantId(), roles, warehouseIds);
        return new TerminalSwitchResponse(
                access,
                target.getTenantId(),
                target.getId(),
                roles,
                warehouseIds,
                ttlMinutes * 60,
                "TERMINAL_SWITCH",
                switchedFrom);
    }

    @Transactional
    public void setOwnTerminalPin(SetTerminalPinRequest request) {
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        setTerminalPin(userId, request.pin());
    }

    @Transactional
    public void setTerminalPin(UUID userId, String pin) {
        UUID tenantId = TenantContext.requireTenantId();
        if (pin == null || !pin.matches("^\\d{4}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PIN", "PIN must be exactly 4 digits");
        }
        String pinHash = hashTerminalPin(tenantId, pin);
        userRepository.findByTenantIdAndTerminalPinHash(tenantId, pinHash).ifPresent(existing -> {
            if (!existing.getId().equals(userId)) {
                throw new ApiException(HttpStatus.CONFLICT, "PIN_IN_USE",
                        "This terminal PIN is already assigned to another operator");
            }
        });
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (!tenantId.equals(user.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
        user.setTerminalPinHash(pinHash);
        userRepository.save(user);
    }

    public static String hashTerminalPin(UUID tenantId, String pin) {
        return hashToken(tenantId + ":" + pin);
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
        return new TokenResponse(access, refresh, user.getTenantId(), user.getId(), roles, warehouseIds,
                user.getAvatarUrl());
    }

    @Transactional(readOnly = true)
    public MeResponse currentUser() {
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        List<String> roles = userRoleRepository.findRoleCodesByUserId(userId);
        List<UUID> warehouseIds = resolveWarehouseIds(user.getTenantId(), user.getId(), roles);
        return new MeResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                warehouseIds,
                user.getAvatarUrl());
    }

    @Transactional
    public User updateMyAvatar(String avatarUrl) {
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        String normalized = avatarUrl == null || avatarUrl.isBlank()
                ? null
                : mediaUrlValidator.validateAndNormalize(avatarUrl);
        user.setAvatarUrl(normalized);
        return userRepository.save(user);
    }

    /**
     * OWNER/ADMIN see all tenant warehouses. Localized roles are restricted to user_warehouses mappings.
     * Active vehicle assignments are appended so technicians can scope X-Warehouse-Id to their van.
     */
    public List<UUID> resolveWarehouseIds(UUID tenantId, UUID userId, List<String> roles) {
        List<UUID> ids = new ArrayList<>(roles.contains("OWNER") || roles.contains("ADMIN")
                ? bootstrapJdbc.findAllWarehouseIds(tenantId)
                : bootstrapJdbc.findWarehouseIdsForUser(tenantId, userId));
        vehicleAssignmentRepository
                .findByTenantIdAndTechnicianUserIdAndReturnedAtIsNull(tenantId, userId)
                .ifPresent(assignment -> {
                    if (!ids.contains(assignment.getLocationId())) {
                        ids.add(assignment.getLocationId());
                    }
                });
        return ids;
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
