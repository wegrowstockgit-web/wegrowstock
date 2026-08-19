package com.invsys.core.security;

import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.MeResponse;
import com.invsys.core.security.dto.RefreshRequest;
import com.invsys.core.security.dto.SetTerminalPinRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TerminalSwitchRequest;
import com.invsys.core.security.dto.TerminalSwitchResponse;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.security.dto.WarehouseLoginRequest;
import com.invsys.core.common.ApiException;
import com.invsys.config.JwtProperties;
import com.invsys.domain.RefreshToken;
import com.invsys.domain.User;
import com.invsys.domain.NetworkAccessLevel;
import com.invsys.media.MediaUrlValidator;
import com.invsys.repository.RefreshTokenRepository;
import com.invsys.repository.TenantRepository;
import com.invsys.repository.UserRepository;
import com.invsys.repository.UserRoleRepository;
import com.invsys.repository.VehicleAssignmentRepository;
import com.invsys.service.RolePermissionService;
import com.invsys.service.TenantOnboardingService;
import com.invsys.service.TenantSubscriptionService;
import com.invsys.service.TerminalBiometricService;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final TenantSsoResolver tenantSsoResolver;
    private final TerminalPinBruteForceGuard terminalPinBruteForceGuard;
    private final RolePermissionService rolePermissionService;
    private final TenantSubscriptionService tenantSubscriptionService;
    private final ImpersonationHandoffStore impersonationHandoffStore;
    private final NetworkAccessPolicy networkAccessPolicy;
    private final TerminalBiometricService terminalBiometricService;
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
                       TenantSsoResolver tenantSsoResolver,
                       TerminalPinBruteForceGuard terminalPinBruteForceGuard,
                       RolePermissionService rolePermissionService,
                       TenantSubscriptionService tenantSubscriptionService,
                       ImpersonationHandoffStore impersonationHandoffStore,
                       NetworkAccessPolicy networkAccessPolicy,
                       @Lazy TerminalBiometricService terminalBiometricService,
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
        this.tenantSsoResolver = tenantSsoResolver;
        this.mediaUrlValidator = mediaUrlValidator;
        this.terminalPinBruteForceGuard = terminalPinBruteForceGuard;
        this.rolePermissionService = rolePermissionService;
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.impersonationHandoffStore = impersonationHandoffStore;
        this.networkAccessPolicy = networkAccessPolicy;
        this.terminalBiometricService = terminalBiometricService;
        this.self = self;
    }

    public TokenResponse signup(SignupRequest request) {
        TenantOnboardingService.OnboardingResult result = onboardingService.signup(request);
        return issueTokens(result.user(), result.roles());
    }

    public TokenResponse login(LoginRequest request) {
        return login(request, null);
    }

    public TokenResponse login(LoginRequest request, String clientIp) {
        var authUser = bootstrapJdbc.findUserForAuthByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials"));

        UUID tenantId = authUser.tenantId();

        // Domain-routed force-SSO — only when the domain route belongs to THIS user's tenant
        // (prevents attacker-tenant domain registration from locking out victims).
        tenantSsoResolver.resolveByEmail(request.email())
                .filter(TenantSsoResolver.SsoRoute::forceSso)
                .filter(route -> tenantId.equals(route.tenantId()))
                .ifPresent(route -> {
                    throw new ApiException(HttpStatus.FORBIDDEN, "SSO_REQUIRED",
                            "Corporate SSO is required for this domain")
                            .withProperty("ssoAuthorizationUrl", route.authorizationUrl());
                });

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
            boolean mfaVerified = enforceLoginNetworkFence(request, clientIp, tenantId, authUser.id());
            TokenResponse tokens = self.completeLogin(authUser.id(), request.targetApp(), mfaVerified);
            AuthService.assertTargetAppAccess(request.targetApp(), tokens);
            return tokens;
        } finally {
            TenantContext.clear();
        }
    }

    private boolean enforceLoginNetworkFence(LoginRequest request, String clientIp, UUID tenantId, UUID userId) {
        List<String> roles = userRoleRepository.findRoleCodesByUserId(userId);
        NetworkAccessLevel level = networkAccessPolicy.highestForRoleCodes(tenantId, roles);
        List<String> cidrs = networkAccessPolicy.allowedCidrBlocks(tenantId);
        NetworkAccessPolicy.Decision decision = networkAccessPolicy.evaluate(clientIp, cidrs, level, false);
        if (decision == NetworkAccessPolicy.Decision.ALLOW) {
            return false;
        }
        if (decision == NetworkAccessPolicy.Decision.DENY_STRICT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", NetworkAccessPolicy.STRICT_DENIED_DETAIL);
        }
        if (request.hasMfaAssertion()) {
            terminalBiometricService.verifyLoginMfa(
                    userId, request.mfaCredentialId(), request.mfaChallenge(), request.mfaSignature());
            return true;
        }
        ApiException ex = new ApiException(
                HttpStatus.UNAUTHORIZED, NetworkAccessPolicy.MFA_REQUIRED_CODE, NetworkAccessPolicy.MFA_REQUIRED_CODE);
        terminalBiometricService.createLoginMfaChallenge(userId).forEach(ex::withProperty);
        throw ex;
    }

    /**
     * Cross-app login gate. {@code null}/blank {@code targetApp} keeps existing clients working.
     */
    public static void assertTargetAppAccess(String targetApp, TokenResponse tokens) {
        if (targetApp == null || targetApp.isBlank() || tokens == null) {
            return;
        }
        String app = targetApp.trim().toUpperCase(Locale.ROOT);
        List<String> roles = tokens.roles() == null ? List.of() : tokens.roles();
        List<String> permissions = tokens.grantedPermissions() == null ? List.of() : tokens.grantedPermissions();
        switch (app) {
            case "POS" -> {
                if (!hasPosOperate(roles, permissions)) {
                    throw new AccessDeniedException("User does not have POS access privileges.");
                }
            }
            case "WMS" -> {
                if (!hasWmsAccess(roles, permissions)) {
                    throw new AccessDeniedException("User does not have WMS access privileges.");
                }
            }
            case "ADMIN" -> throw new AccessDeniedException("User does not have admin access privileges.");
            default -> {
            }
        }
    }

    static boolean hasPosOperate(List<String> roles, List<String> permissions) {
        if (roles.contains("OWNER") || roles.contains("ADMIN")) {
            return true;
        }
        return permissions.contains(PermissionKeys.POS_OPERATE);
    }

    static boolean hasWmsAccess(List<String> roles, List<String> permissions) {
        if (roles.stream().anyMatch(WMS_LOGIN_ROLES::contains)) {
            return true;
        }
        return permissions.stream().anyMatch(AuthService::isWmsPermission);
    }

    static String normalizeAppContext(String targetApp) {
        if (targetApp == null || targetApp.isBlank()) {
            return null;
        }
        String app = targetApp.trim().toUpperCase(Locale.ROOT);
        return switch (app) {
            case "POS", "WMS" -> app;
            default -> null;
        };
    }

    static boolean isWmsPermission(String permissionKey) {
        return permissionKey != null
                && PermissionKeys.CATALOG.contains(permissionKey)
                && !PermissionKeys.POS_OPERATE.equals(permissionKey)
                && !PermissionKeys.POS_SUPERVISE.equals(permissionKey);
    }

    private static final Set<String> WMS_LOGIN_ROLES = Set.of(
            "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER", "VIEWER", "B2B_CUSTOMER", "SUPPLIER");

    @Transactional
    public TokenResponse completeLogin(UUID userId) {
        return completeLogin(userId, null, false);
    }

    @Transactional
    public TokenResponse completeLogin(UUID userId, String targetApp) {
        return completeLogin(userId, targetApp, false);
    }

    @Transactional
    public TokenResponse completeLogin(UUID userId, String targetApp, boolean mfaVerified) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials"));
        List<String> roles = userRoleRepository.findRoleCodesByUserId(userId);
        return issueTokens(user, roles, targetApp, mfaVerified);
    }

    /**
     * Exchanges a control-plane impersonation JWT or one-time handoff code for a WMS session.
     */
    @Transactional
    public TokenResponse acceptImpersonation(String impersonationTokenOrCode) {
        if (impersonationTokenOrCode == null || impersonationTokenOrCode.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", "impersonation token required");
        }
        String jwt = impersonationTokenOrCode.trim();
        if (!looksLikeJwt(jwt)) {
            jwt = impersonationHandoffStore.redeemCode(jwt)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                            "Invalid or already used impersonation code"));
        }
        var claims = jwtService.validateAndParse(jwt);
        Object type = claims.getClaim(JwtService.CLAIM_TOKEN_TYPE);
        if (!JwtService.TOKEN_TYPE_IMPERSONATION.equals(type)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Not an impersonation token");
        }
        String jti = claims.getJWTID();
        if (jti == null || jti.isBlank() || !impersonationHandoffStore.consumeJti(jti)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_CONSUMED",
                    "Impersonation token already used or revoked");
        }
        UUID userId = UUID.fromString(claims.getSubject());
        UUID tenantId = UUID.fromString((String) claims.getClaim(JwtService.CLAIM_TENANT_ID));
        if (bootstrapJdbc.isTenantSuspended(tenantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_SUSPENDED", "Tenant is suspended");
        }
        TenantContext.setTenantId(tenantId);
        try {
            return self.completeLogin(userId, "WMS");
        } finally {
            TenantContext.clear();
        }
    }

    private static boolean looksLikeJwt(String value) {
        int dots = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '.') {
                dots++;
            }
        }
        return dots == 2;
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = hashToken(request.refreshToken());
        var stored = bootstrapJdbc.findRefreshTokenByHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid refresh token"));
        if (stored.revokedAt() != null) {
            TenantContext.setTenantId(stored.tenantId());
            try {
                refreshTokenRepository.revokeAllForUser(stored.userId(), Instant.now());
            } finally {
                TenantContext.clear();
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid refresh token");
        }
        if (stored.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid refresh token");
        }
        // /auth/refresh bypasses JwtAuthFilter — bind + clear tenant explicitly for RLS + VT safety.
        TenantContext.setTenantId(stored.tenantId());
        TenantContext.setUserId(stored.userId());
        try {
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
            String appContext = tokenEntity.getAppContext();
            boolean mfaVerified = tokenEntity.isMfaVerified();
            String access = jwtService.generateAccessToken(
                    user.getId(), user.getTenantId(), roles, warehouseIds, appContext, mfaVerified);
            String refresh = UUID.randomUUID().toString();
            RefreshToken replacement = new RefreshToken();
            replacement.setTenantId(user.getTenantId());
            replacement.setUserId(user.getId());
            replacement.setTokenHash(hashToken(refresh));
            replacement.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenDays() * 86400L));
            replacement.setAppContext(appContext);
            replacement.setMfaVerified(mfaVerified);
            replacement = refreshTokenRepository.save(replacement);
            tokenEntity.setReplacedBy(replacement.getId());
            refreshTokenRepository.save(tokenEntity);
            return new TokenResponse(access, refresh, user.getTenantId(), user.getId(), roles, warehouseIds,
                    user.getAvatarUrl(),
                    rolePermissionService.resolveGrantedPermissions(user.getTenantId(), roles));
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        String hash = hashToken(refreshToken);
        var stored = bootstrapJdbc.findRefreshTokenByHash(hash);
        if (stored.isEmpty()) {
            return;
        }
        TenantContext.setTenantId(stored.get().tenantId());
        try {
            refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            });
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Shared-terminal PIN pad: swap operator JWT context without issuing a new refresh token
     * (primary device session remains intact on the client).
     */
    /**
     * Surface B warehouse login: email + 4-digit PIN → full session cookies.
     * Lockout keyed by email (and optional deviceId).
     */
    public TokenResponse warehouseLogin(WarehouseLoginRequest request) {
        String lockKey = request.deviceId() != null && !request.deviceId().isBlank()
                ? request.deviceId().trim()
                : request.email().trim().toLowerCase();
        terminalPinBruteForceGuard.assertCredentialAllowed(lockKey);

        var authUser = bootstrapJdbc.findUserForAuthByEmail(request.email().trim().toLowerCase())
                .orElse(null);
        if (authUser == null) {
            terminalPinBruteForceGuard.recordCredentialFailure(lockKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PIN", "Invalid terminal PIN");
        }
        UUID tenantId = authUser.tenantId();
        String pinHash = hashTerminalPin(tenantId, request.pin());
        TenantContext.setTenantId(tenantId);
        try {
            User target = userRepository.findByTenantIdAndTerminalPinHash(tenantId, pinHash).orElse(null);
            if (target == null
                    || !target.getId().equals(authUser.id())
                    || !"ACTIVE".equals(target.getStatus())) {
                terminalPinBruteForceGuard.recordCredentialFailure(lockKey);
                throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PIN", "Invalid terminal PIN");
            }
            terminalPinBruteForceGuard.recordCredentialSuccess(lockKey);
            return self.completeLogin(target.getId(), "WMS");
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional(readOnly = true)
    public TerminalSwitchResponse terminalSwitch(TerminalSwitchRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID switchedFrom = TenantContext.getUserId().orElse(null);
        terminalPinBruteForceGuard.assertAllowed(tenantId, switchedFrom);

        String pinHash = hashTerminalPin(tenantId, request.pin());
        User target = userRepository.findByTenantIdAndTerminalPinHash(tenantId, pinHash).orElse(null);
        // Uniform failure — do not distinguish missing PIN vs inactive operator (enumeration).
        if (target == null || !"ACTIVE".equals(target.getStatus())) {
            terminalPinBruteForceGuard.recordFailure(tenantId, switchedFrom);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PIN", "Invalid terminal PIN");
        }

        // Hard multi-tenant boundary: PIN lookup is tenant-scoped; refuse any hop.
        if (!tenantId.equals(target.getTenantId())) {
            terminalPinBruteForceGuard.recordFailure(tenantId, switchedFrom);
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH",
                    "Terminal switch target is outside the active tenant");
        }

        terminalPinBruteForceGuard.recordSuccess(tenantId, switchedFrom);
        List<String> roles = userRoleRepository.findRoleCodesByUserId(target.getId());
        List<UUID> warehouseIds = resolveWarehouseIds(tenantId, target.getId(), roles);
        return buildTerminalSwitchResponse(tenantId, target, roles, warehouseIds, switchedFrom);
    }

    public String issueTerminalAccessToken(UUID sessionTenantId, User user, List<String> roles, List<UUID> warehouseIds) {
        if (sessionTenantId == null || user == null || !sessionTenantId.equals(user.getTenantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH",
                    "Terminal switch target is outside the active tenant");
        }
        return jwtService.generateTerminalSwitchToken(user.getId(), sessionTenantId, roles, warehouseIds);
    }

    TerminalSwitchResponse buildTerminalSwitchResponse(UUID sessionTenantId,
                                                       User target,
                                                       List<String> roles,
                                                       List<UUID> warehouseIds,
                                                       UUID switchedFrom) {
        if (!sessionTenantId.equals(target.getTenantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_MISMATCH",
                    "Terminal switch target is outside the active tenant");
        }
        int ttlMinutes = jwtProperties.getTerminalSwitchTokenMinutes();
        String access = jwtService.generateTerminalSwitchToken(
                target.getId(), sessionTenantId, roles, warehouseIds);
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
        return issueTokens(user, roles, null, false);
    }

    private TokenResponse issueTokens(User user, List<String> roles, String targetApp) {
        return issueTokens(user, roles, targetApp, false);
    }

    private TokenResponse issueTokens(User user, List<String> roles, String targetApp, boolean mfaVerified) {
        List<String> roleList = roles == null ? List.of() : List.copyOf(roles);
        List<UUID> warehouseIds = resolveWarehouseIds(user.getTenantId(), user.getId(), roleList);
        List<String> grantedPermissions = rolePermissionService.resolveGrantedPermissions(
                user.getTenantId(), roles);
        String appContext = normalizeAppContext(targetApp);
        String access = jwtService.generateAccessToken(
                user.getId(), user.getTenantId(), roleList, warehouseIds, appContext, mfaVerified);
        String refresh = UUID.randomUUID().toString();
        RefreshToken entity = new RefreshToken();
        entity.setTenantId(user.getTenantId());
        entity.setUserId(user.getId());
        entity.setTokenHash(hashToken(refresh));
        entity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenDays() * 86400L));
        entity.setAppContext(appContext);
        entity.setMfaVerified(mfaVerified);
        refreshTokenRepository.save(entity);
        return new TokenResponse(access, refresh, user.getTenantId(), user.getId(), roleList, warehouseIds,
                user.getAvatarUrl(), grantedPermissions);
    }

    @Transactional(readOnly = true)
    public MeResponse currentUser() {
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        List<String> roles = List.copyOf(userRoleRepository.findRoleCodesByUserId(userId));
        List<UUID> warehouseIds = resolveWarehouseIds(user.getTenantId(), user.getId(), roles);
        List<String> grantedPermissions = rolePermissionService.resolveGrantedPermissions(
                user.getTenantId(), userRoleRepository.findRoleCodesByUserId(userId));
        return new MeResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getDisplayName(),
                roles,
                warehouseIds,
                user.getAvatarUrl(),
                user.getDepartment(),
                user.getCorporateDepartment(),
                user.getTimezonePreference(),
                user.getLocaleLanguage(),
                user.getAssignedWarehouseId(),
                user.isMfaEnabled(),
                user.getShiftSchedule(),
                user.getShiftScheduleType(),
                user.getPhone(),
                user.getAddressLine1(),
                user.getAddressLine2(),
                user.getAddressCity(),
                user.getAddressRegion(),
                user.getAddressPostalCode(),
                user.getAddressCountry(),
                user.getUiDensityPreference(),
                grantedPermissions,
                false,
                tenantSubscriptionService.getEnabledModules(user.getTenantId()),
                tenantSubscriptionService.getCommercialTier(user.getTenantId()).name());
    }

    /**
     * Self-service personal profile only. Organizational fields (role, warehouses, department,
     * timezone, locale, shift) must be changed via admin org-scope APIs.
     */
    @Transactional
    public User updateMyProfile(
            String displayName,
            String phone,
            String addressLine1,
            String addressLine2,
            String addressCity,
            String addressRegion,
            String addressPostalCode,
            String addressCountry,
            Boolean mfaEnabled,
            String uiDensityPreference,
            String localeLanguage) {
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (displayName != null) {
            if (displayName.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "displayName cannot be blank");
            }
            user.setDisplayName(displayName.trim());
        }
        if (phone != null) {
            user.setPhone(phone.isBlank() ? null : phone.trim());
        }
        if (addressLine1 != null) {
            user.setAddressLine1(addressLine1.isBlank() ? null : addressLine1.trim());
        }
        if (addressLine2 != null) {
            user.setAddressLine2(addressLine2.isBlank() ? null : addressLine2.trim());
        }
        if (addressCity != null) {
            user.setAddressCity(addressCity.isBlank() ? null : addressCity.trim());
        }
        if (addressRegion != null) {
            user.setAddressRegion(addressRegion.isBlank() ? null : addressRegion.trim());
        }
        if (addressPostalCode != null) {
            user.setAddressPostalCode(addressPostalCode.isBlank() ? null : addressPostalCode.trim());
        }
        if (addressCountry != null) {
            String country = addressCountry.isBlank() ? null : addressCountry.trim().toUpperCase();
            if (country != null && country.length() != 2) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                        "addressCountry must be a 2-letter ISO code");
            }
            user.setAddressCountry(country);
        }
        if (mfaEnabled != null) {
            user.setMfaEnabled(mfaEnabled);
        }
        if (uiDensityPreference != null) {
            String density = uiDensityPreference.isBlank() ? null : uiDensityPreference.trim().toUpperCase();
            if (density != null
                    && !density.equals("COMPACT")
                    && !density.equals("COMFORTABLE")
                    && !density.equals("SPACIOUS")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                        "uiDensityPreference must be COMPACT, COMFORTABLE, or SPACIOUS");
            }
            user.setUiDensityPreference(density);
        }
        if (localeLanguage != null) {
            user.setLocaleLanguage(normalizePreferredLanguage(localeLanguage));
        }
        return userRepository.save(user);
    }

    static String normalizePreferredLanguage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "en";
        }
        String token = raw.trim().toLowerCase().replace('_', '-');
        if (token.startsWith("es")) {
            return "es";
        }
        if (token.startsWith("fr")) {
            return "fr";
        }
        if (token.startsWith("en")) {
            return "en";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                "localeLanguage must be en, es, or fr");
    }

    @Transactional
    public void changeMyPassword(String currentPassword, String newPassword) {
        UUID userId = TenantContext.getUserId()
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Not authenticated"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        if (currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION",
                    "currentPassword is required and newPassword must be at least 8 characters");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, Instant.now());
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
