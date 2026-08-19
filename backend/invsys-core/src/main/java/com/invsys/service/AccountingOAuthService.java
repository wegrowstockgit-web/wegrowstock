package com.invsys.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import com.invsys.integration.domain.IntegrationCredential;
import com.invsys.integration.repository.IntegrationCredentialRepository;
import com.invsys.repository.IntegrationSyncLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountingOAuthService {

    public static final Set<String> PROVIDERS = Set.of(
            "QUICKBOOKS", "XERO", "NETSUITE", "SHOPIFY", "AMAZON", "STRIPE", "AS2");

    private static final Duration TOKEN_EXPIRING_WINDOW = Duration.ofDays(7);

    private final BootstrapJdbc bootstrapJdbc;
    private final IntegrationCredentialRepository credentialRepository;
    private final IntegrationSyncLogRepository syncLogRepository;
    private final String redirectBase;
    private final String quickBooksClientId;
    private final String quickBooksAuthorizeUrl;
    private final String quickBooksScope;
    private final String xeroClientId;
    private final String xeroAuthorizeUrl;
    private final String xeroScope;

    public AccountingOAuthService(
            BootstrapJdbc bootstrapJdbc,
            IntegrationCredentialRepository credentialRepository,
            IntegrationSyncLogRepository syncLogRepository,
            @Value("${invsys.integration.accounting.oauth.redirect-uri:http://localhost:3000/api/v1/public/oauth/callback}")
            String redirectBase,
            @Value("${invsys.integration.accounting.oauth.quickbooks.client-id:sandbox-qbo-client}")
            String quickBooksClientId,
            @Value("${invsys.integration.accounting.oauth.quickbooks.authorize-url:https://appcenter.intuit.com/connect/oauth2}")
            String quickBooksAuthorizeUrl,
            @Value("${invsys.integration.accounting.oauth.quickbooks.scope:com.intuit.quickbooks.accounting}")
            String quickBooksScope,
            @Value("${invsys.integration.accounting.oauth.xero.client-id:sandbox-xero-client}")
            String xeroClientId,
            @Value("${invsys.integration.accounting.oauth.xero.authorize-url:https://login.xero.com/identity/connect/authorize}")
            String xeroAuthorizeUrl,
            @Value("${invsys.integration.accounting.oauth.xero.scope:offline_access accounting.transactions accounting.settings}")
            String xeroScope) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.credentialRepository = credentialRepository;
        this.syncLogRepository = syncLogRepository;
        this.redirectBase = redirectBase;
        this.quickBooksClientId = quickBooksClientId;
        this.quickBooksAuthorizeUrl = quickBooksAuthorizeUrl;
        this.quickBooksScope = quickBooksScope;
        this.xeroClientId = xeroClientId;
        this.xeroAuthorizeUrl = xeroAuthorizeUrl;
        this.xeroScope = xeroScope;
    }

    public AuthUrl authUrl(String provider) {
        String normalized = normalizeProvider(provider);
        UUID tenantId = TenantContext.requireTenantId();
        String state = mintState(tenantId, normalized);
        String redirectUri = redirectUri(normalized);
        String url = switch (normalized) {
            case "QUICKBOOKS" -> authorizeUrl(
                    quickBooksAuthorizeUrl, quickBooksClientId, redirectUri, quickBooksScope, state);
            case "XERO" -> authorizeUrl(xeroAuthorizeUrl, xeroClientId, redirectUri, xeroScope, state);
            case "SHOPIFY" -> "https://accounts.shopify.com/oauth/authorize?client_id=sandbox&state=" + state;
            case "AMAZON" -> "https://sellercentral.amazon.com/apps/authorize/consent?state=" + state;
            case "NETSUITE" -> "https://system.netsuite.com/app/login/oauth2/authorize.nl?state=" + state;
            case "STRIPE" -> "https://connect.stripe.com/oauth/authorize?response_type=code&state=" + state;
            case "AS2" -> "/settings?tab=operations";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER",
                    "Unsupported integration provider: " + normalized);
        };
        return new AuthUrl(url, state, normalized);
    }

    public ConnectionStatus status(String provider) {
        String normalized = normalizeProvider(provider);
        UUID tenantId = TenantContext.requireTenantId();
        Optional<IntegrationCredential> credential = findCredential(tenantId, normalized);
        boolean connected = credential.filter(c -> "CONNECTED".equalsIgnoreCase(c.getStatus())).isPresent();
        boolean tokenExpiringSoon = credential
                .map(IntegrationCredential::getRefreshTokenExpiresAt)
                .filter(expires -> expires.isBefore(Instant.now().plus(TOKEN_EXPIRING_WINDOW)))
                .isPresent();
        Instant lastSyncAt = syncLogRepository
                .findFirstByTenantIdAndSystemOrderByCreatedAtDesc(tenantId, normalized)
                .map(log -> log.getProcessedAt() != null ? log.getProcessedAt() : log.getCreatedAt())
                .orElse(null);
        return new ConnectionStatus(
                connected,
                connected ? displayName(normalized) : "",
                lastSyncAt == null ? "" : lastSyncAt.toString(),
                tokenExpiringSoon);
    }

    public static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "provider is required");
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        if ("QBO".equals(normalized) || "QUICKBOOKS_ONLINE".equals(normalized)) {
            normalized = "QUICKBOOKS";
        }
        if (!PROVIDERS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PROVIDER",
                    "Unsupported integration provider: " + normalized);
        }
        return normalized;
    }

    public static String catalogSystem(String storedSystem) {
        if (storedSystem == null) {
            return "";
        }
        String normalized = storedSystem.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("OAUTH_") ? normalized.substring("OAUTH_".length()) : normalized;
    }

    private Optional<IntegrationCredential> findCredential(UUID tenantId, String system) {
        return credentialRepository.findByTenantIdAndSystem(tenantId, system)
                .or(() -> credentialRepository.findByTenantIdAndSystem(tenantId, "OAUTH_" + system));
    }

    private String mintState(UUID tenantId, String provider) {
        String state = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        bootstrapJdbc.insertOauthCallbackState(
                state, tenantId, provider.toLowerCase(Locale.ROOT), "{}", Instant.now().plusSeconds(600));
        return state;
    }

    private String redirectUri(String provider) {
        String base = redirectBase.endsWith("/") ? redirectBase.substring(0, redirectBase.length() - 1) : redirectBase;
        return base + "/" + provider.toLowerCase(Locale.ROOT);
    }

    private static String authorizeUrl(String authorizeUrl, String clientId, String redirectUri,
                                       String scope, String state) {
        return UriComponentsBuilder.fromUriString(authorizeUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", scope)
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    private static String displayName(String provider) {
        return switch (provider) {
            case "QUICKBOOKS" -> "QuickBooks Online";
            case "XERO" -> "Xero";
            case "NETSUITE" -> "NetSuite";
            case "SHOPIFY" -> "Shopify";
            case "AMAZON" -> "Amazon Seller Central";
            case "STRIPE" -> "Stripe";
            case "AS2" -> "AS2 Trading Partners";
            default -> provider;
        };
    }

    public record AuthUrl(String authorizationUrl, String state, String provider) {
    }

    public record ConnectionStatus(
            boolean connected,
            String accountName,
            String lastSyncAt,
            boolean tokenExpiringSoon
    ) {
    }
}