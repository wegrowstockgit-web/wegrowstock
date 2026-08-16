package com.invsys.core.security;

import com.invsys.core.security.dto.TokenResponse;
import com.invsys.core.common.ApiException;
import com.invsys.domain.MagicLoginToken;
import com.invsys.repository.MagicLoginTokenRepository;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.service.InvitationEmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MagicLoginService {

    private static final int EXPIRY_MINUTES = 15;

    private final BootstrapJdbc bootstrapJdbc;
    private final MagicLoginTokenRepository magicLoginTokenRepository;
    private final AuthService authService;
    private final InvitationEmailService invitationEmailService;
    private final boolean exposeMagicToken;
    private final MagicLoginService self;

    public MagicLoginService(BootstrapJdbc bootstrapJdbc,
                             MagicLoginTokenRepository magicLoginTokenRepository,
                             AuthService authService,
                             InvitationEmailService invitationEmailService,
                             @Value("${invsys.security.expose-magic-token:false}") boolean exposeMagicToken,
                             @Lazy MagicLoginService self) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.magicLoginTokenRepository = magicLoginTokenRepository;
        this.authService = authService;
        this.invitationEmailService = invitationEmailService;
        this.exposeMagicToken = exposeMagicToken;
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

        invitationEmailService.sendMagicLink(email, invitationEmailService.magicLoginUrl(plaintext));
        if (exposeMagicToken) {
            response.put("magicToken", plaintext);
        }
        return response;
    }

    /**
     * Issues a welcome magic link for a just-provisioned wholesale buyer.
     * Does not clear the caller's {@link TenantContext}.
     */
    public Map<String, Object> issueWelcomeMagicLink(UUID tenantId, UUID userId, String email) {
        String plaintext = UUID.randomUUID().toString() + UUID.randomUUID();
        String hash = AuthService.hashToken(plaintext);
        self.persistToken(tenantId, userId, hash);
        invitationEmailService.sendWholesaleWelcome(email, invitationEmailService.wholesaleWelcomeUrl(plaintext));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "accepted");
        if (exposeMagicToken) {
            response.put("magicToken", plaintext);
        }
        return response;
    }

    public TokenResponse consumeMagicLink(String token) {
        String hash = AuthService.hashToken(token);
        var row = bootstrapJdbc.findMagicLoginTokenByHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Invalid magic login token"));

        if (row.expiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Magic login token expired");
        }

        TenantContext.setTenantId(row.tenantId());
        try {
            if (!self.markConsumedIfUnused(hash)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_CONSUMED", "Magic login token already used");
            }
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
    public boolean markConsumedIfUnused(String hash) {
        return magicLoginTokenRepository.consumeIfUnused(hash, Instant.now()) == 1;
    }
}
