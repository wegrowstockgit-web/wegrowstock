package com.invsys.tenancy;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pre-authentication lookups using the migration owner connection.
 * Required because RLS blocks app_user until tenant context is established.
 */
@Component
public class BootstrapJdbc {

    private final JdbcTemplate jdbc;

    public BootstrapJdbc(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
    }

    public Optional<UUID> findTenantIdBySlug(String slug) {
        return jdbc.query(
                "SELECT id FROM tenants WHERE slug = ?",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                slug);
    }

    public Optional<UserAuthRow> findUserForAuth(UUID tenantId, String email) {
        return jdbc.query(
                "SELECT id, password_hash, status FROM users WHERE tenant_id = ? AND lower(email) = lower(?)",
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new UserAuthRow(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("password_hash"),
                            rs.getString("status")));
                },
                tenantId, email);
    }

    /**
     * Slugless login: resolve tenant + credentials from globally unique email.
     */
    public Optional<UserAuthWithTenantRow> findUserForAuthByEmail(String email) {
        return jdbc.query(
                """
                SELECT id, tenant_id, password_hash, status
                FROM users WHERE lower(email) = lower(?)
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new UserAuthWithTenantRow(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            rs.getString("password_hash"),
                            rs.getString("status")));
                },
                email);
    }

    public List<UUID> findWarehouseIdsForUser(UUID tenantId, UUID userId) {
        return jdbc.query(
                "SELECT location_id FROM user_warehouses WHERE tenant_id = ? AND user_id = ?",
                (rs, rowNum) -> UUID.fromString(rs.getString(1)),
                tenantId, userId);
    }

    public List<UUID> findAllWarehouseIds(UUID tenantId) {
        return jdbc.query(
                "SELECT id FROM locations WHERE tenant_id = ? AND type = 'WAREHOUSE' ORDER BY code",
                (rs, rowNum) -> UUID.fromString(rs.getString(1)),
                tenantId);
    }

    public Optional<RefreshTokenRow> findRefreshTokenByHash(String tokenHash) {
        return jdbc.query(
                """
                SELECT tenant_id, user_id, expires_at, revoked_at
                FROM refresh_tokens WHERE token_hash = ?
                """,
                rs -> {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(new RefreshTokenRow(
                            UUID.fromString(rs.getString("tenant_id")),
                            UUID.fromString(rs.getString("user_id")),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("revoked_at") != null
                                    ? rs.getTimestamp("revoked_at").toInstant() : null));
                },
                tokenHash);
    }

    public Optional<UUID> findTenantIdByChannelShop(String platform, String shopIdentifier) {
        return jdbc.query(
                "SELECT tenant_id FROM channel_integrations WHERE platform = ? AND shop_identifier = ? AND status = 'ACTIVE'",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                platform, shopIdentifier);
    }

    public Optional<SsoBootstrapRow> findSsoConfigByTenantId(UUID tenantId) {
        return jdbc.query(
                """
                SELECT issuer_url, client_id, encrypted_client_secret, enabled, force_sso,
                       COALESCE(protocol, 'OIDC') AS protocol, saml_metadata_url, saml_entity_id
                FROM tenant_sso_configs WHERE tenant_id = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new SsoBootstrapRow(
                            rs.getString("issuer_url"),
                            rs.getString("client_id"),
                            rs.getBytes("encrypted_client_secret"),
                            rs.getBoolean("enabled"),
                            rs.getBoolean("force_sso"),
                            rs.getString("protocol"),
                            rs.getString("saml_metadata_url"),
                            rs.getString("saml_entity_id")
                    ));
                },
                tenantId);
    }

    public Optional<MagicLoginTokenRow> findMagicLoginTokenByHash(String tokenHash) {
        return jdbc.query(
                """
                SELECT id, tenant_id, user_id, expires_at, consumed_at
                FROM magic_login_tokens WHERE token_hash = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new MagicLoginTokenRow(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            UUID.fromString(rs.getString("user_id")),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("consumed_at") != null
                                    ? rs.getTimestamp("consumed_at").toInstant() : null));
                },
                tokenHash);
    }

    public Optional<InvoiceBootstrapRow> findInvoiceByNumberOrId(String numberOrId) {
        if (numberOrId == null || numberOrId.isBlank()) {
            return Optional.empty();
        }
        String trimmed = numberOrId.trim();
        try {
            UUID id = UUID.fromString(trimmed);
            Optional<InvoiceBootstrapRow> byId = jdbc.query(
                    "SELECT id, tenant_id, number, status FROM invoices WHERE id = ?",
                    rs -> rs.next() ? Optional.of(new InvoiceBootstrapRow(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            rs.getString("number"),
                            rs.getString("status"))) : Optional.empty(),
                    id);
            if (byId.isPresent()) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
            // treat as invoice number
        }
        return jdbc.query(
                "SELECT id, tenant_id, number, status FROM invoices WHERE number = ? LIMIT 1",
                rs -> rs.next() ? Optional.of(new InvoiceBootstrapRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("tenant_id")),
                        rs.getString("number"),
                        rs.getString("status"))) : Optional.empty(),
                trimmed);
    }

    public record UserAuthRow(UUID id, String passwordHash, String status) {}

    public record UserAuthWithTenantRow(UUID id, UUID tenantId, String passwordHash, String status) {}

    public record RefreshTokenRow(UUID tenantId, UUID userId, java.time.Instant expiresAt, java.time.Instant revokedAt) {}

    public record SsoBootstrapRow(String issuerUrl, String clientId, byte[] encryptedClientSecret,
                                 boolean enabled, boolean forceSso, String protocol,
                                 String samlMetadataUrl, String samlEntityId) {
    }

    public record MagicLoginTokenRow(UUID id, UUID tenantId, UUID userId,
                                     java.time.Instant expiresAt, java.time.Instant consumedAt) {
    }

    public record InvoiceBootstrapRow(UUID id, UUID tenantId, String number, String status) {
    }

    public void upsertCurrencyRate(String fromCurrency, String toCurrency, java.math.BigDecimal rate, java.time.Instant asOf) {
        jdbc.update("""
                INSERT INTO currency_rates (id, from_currency, to_currency, rate, as_of, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (from_currency, to_currency)
                DO UPDATE SET rate = EXCLUDED.rate, as_of = EXCLUDED.as_of, updated_at = NOW()
                """, fromCurrency, toCurrency, rate, java.sql.Timestamp.from(asOf));
    }
}
