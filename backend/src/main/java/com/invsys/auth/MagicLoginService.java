package com.invsys.auth;

import com.invsys.auth.dto.TokenResponse;
import com.invsys.common.ApiException;
import com.invsys.domain.MagicLoginToken;
import com.invsys.repository.MagicLoginTokenRepository;
import com.invsys.tenancy.BootstrapJdbc;
import com.invsys.tenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MagicLoginService {

    private static final Logger log = LoggerFactory.getLogger(MagicLoginService.class);
    private static final int EXPIRY_MINUTES = 15;

    private final BootstrapJdbc bootstrapJdbc;
    private final MagicLoginTokenRepository magicLoginTokenRepository;
    private final AuthService authService;
    private final Environment environment;
    private final MagicLoginService self;

    public MagicLoginService(BootstrapJdbc bootstrapJdbc,
                             MagicLoginTokenRepository magicLoginTokenRepository,
                             AuthService authService,
                             Environment environment,
                             @Lazy MagicLoginService self) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.magicLoginTokenRepository = magicLoginTokenRepository;
        this.authService = authService;
        this.environment = environment;
        this.self = self;
    }

    public Map<String, Object> requestMagicLink(String email) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "accepted");

        var authUser = bootstrapJdbc.findUserForAuthByEmail(email);
        if (authUser.isEmpty() || !"ACTIVE".equals(authUser.get().status())) {
            return response;
        }

        String plaintext = UUID.randomUUID().toString() + UUID.randomUUID();
        String hash = AuthService.hashToken(plaintext);

        TenantContext.setTenantId(authUser.get().tenantId());
        try {
            self.persistToken(authUser.get().tenantId(), authUser.get().id(), hash);
        } finally {
            TenantContext.clear();
        }

        if (!isProd()) {
            log.info("Magic login token for {}: {}", email, plaintext);
            response.put("magicToken", plaintext);
        }
        return response;
    }

    public TokenResponse consumeMagicLink(String token) {
        String hash = AuthService.hashToken(token);
        var row = bootstrapJdbc.findMagicLoginTokenByHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid magic login token"));

        if (row.consumedAt() != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_CONSUMED", "Magic login token already used");
        }
        if (row.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Magic login token expired");
        }

        TenantContext.setTenantId(row.tenantId());
        try {
            self.markConsumed(hash);
            return authService.completeLogin(row.userId());
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void persistToken(UUID tenantId, UUID userId, String hash) {
        MagicLoginToken entity = new MagicLoginToken();
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setTokenHash(hash);
        entity.setExpiresAt(Instant.now().plusSeconds(EXPIRY_MINUTES * 60L));
        magicLoginTokenRepository.save(entity);
    }

    @Transactional
    public void markConsumed(String hash) {
        MagicLoginToken entity = magicLoginTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid magic login token"));
        entity.setConsumedAt(Instant.now());
        magicLoginTokenRepository.save(entity);
    }

    private boolean isProd() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
