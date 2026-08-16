package com.invsys.core.tenancy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper JSON = new ObjectMapper();

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

    /**
     * Active tenants for background workers (bypasses RLS via app_owner).
     */
    public List<UUID> listActiveTenantIds() {
        return jdbc.query(
                """
                SELECT id FROM tenants
                WHERE status = 'ACTIVE'
                ORDER BY id
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString(1)));
    }

    public boolean isTenantSuspended(UUID tenantId) {
        Boolean suspended = jdbc.query(
                "SELECT status = 'SUSPENDED' FROM tenants WHERE id = ?",
                rs -> rs.next() && rs.getBoolean(1),
                tenantId);
        return Boolean.TRUE.equals(suspended);
    }

    public void updateTenantStatus(UUID tenantId, String status) {
        jdbc.update("UPDATE tenants SET status = ?, updated_at = NOW() WHERE id = ?", status, tenantId);
    }

    /**
     * Prefer OWNER, else first ACTIVE user — for support impersonation.
     */
    public Optional<ImpersonationUserRow> findImpersonationUser(UUID tenantId) {
        return jdbc.query(
                """
                SELECT u.id, u.email,
                       COALESCE(array_agg(DISTINCT r.code) FILTER (WHERE r.code IS NOT NULL), '{}') AS roles
                  FROM users u
                  LEFT JOIN user_roles ur ON ur.user_id = u.id AND ur.tenant_id = u.tenant_id
                  LEFT JOIN roles r ON r.id = ur.role_id
                 WHERE u.tenant_id = ?
                   AND u.status = 'ACTIVE'
                 GROUP BY u.id, u.email
                 ORDER BY CASE WHEN bool_or(r.code = 'OWNER') THEN 0
                               WHEN bool_or(r.code = 'ADMIN') THEN 1
                               ELSE 2 END,
                          u.email
                 LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    java.sql.Array arr = rs.getArray("roles");
                    String[] codes = arr == null ? new String[0] : (String[]) arr.getArray();
                    return Optional.of(new ImpersonationUserRow(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("email"),
                            java.util.Arrays.asList(codes)));
                },
                tenantId);
    }

    public boolean isIntegrationSyncPaused(UUID tenantId) {
        Boolean paused = jdbc.query(
                "SELECT sync_paused FROM tenant_integration_controls WHERE tenant_id = ?",
                rs -> rs.next() && rs.getBoolean(1),
                tenantId);
        return Boolean.TRUE.equals(paused);
    }

    public double findRateCapacityMultiplier(UUID tenantId) {
        Double mult = jdbc.query(
                "SELECT capacity_multiplier FROM tenant_rate_limit_overrides WHERE tenant_id = ?",
                rs -> rs.next() ? rs.getDouble(1) : null,
                tenantId);
        return mult == null || mult <= 0 ? 1.0 : mult;
    }

    public record ImpersonationUserRow(UUID userId, String email, java.util.List<String> roles) {
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

    /**
     * Resolve tenant + enabled SSO from a verified corporate email domain (e.g. acme.com).
     */
    /**
     * Cross-tenant check (bypasses RLS): another org already verified this email domain.
     */
    public Optional<UUID> findVerifiedDomainOwner(String domainName) {
        if (domainName == null || domainName.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                """
                SELECT tenant_id FROM tenant_domains
                WHERE lower(domain_name) = lower(?)
                  AND verification_status IN ('ACTIVE', 'VERIFIED')
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                domainName.trim());
    }

    /**
     * Cross-tenant (bypasses RLS): DNS-verified domains eligible for dynamic CORS origins.
     */
    public List<String> listActiveVerifiedDomainNames() {
        return jdbc.queryForList(
                """
                SELECT lower(domain_name) AS domain_name
                FROM tenant_domains
                WHERE verification_status IN ('ACTIVE', 'VERIFIED')
                ORDER BY domain_name
                """,
                String.class);
    }

    public Optional<DomainSsoRow> findEnabledSsoByEmailDomain(String domainName) {
        if (domainName == null || domainName.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                """
                SELECT td.tenant_id, s.issuer_url, s.client_id, s.enabled, s.force_sso,
                       COALESCE(s.protocol, 'OIDC') AS protocol
                FROM tenant_domains td
                JOIN tenant_sso_configs s ON s.tenant_id = td.tenant_id
                WHERE lower(td.domain_name) = lower(?)
                  AND td.verification_status IN ('ACTIVE', 'VERIFIED')
                  AND s.enabled = TRUE
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new DomainSsoRow(
                            UUID.fromString(rs.getString("tenant_id")),
                            rs.getString("issuer_url"),
                            rs.getString("client_id"),
                            rs.getBoolean("enabled"),
                            rs.getBoolean("force_sso"),
                            rs.getString("protocol")));
                },
                domainName.trim());
    }

    public Optional<HrdTenantRow> findHrdByVerifiedDomain(String domainName) {
        if (domainName == null || domainName.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                """
                SELECT t.id AS tenant_id, t.name AS company_name,
                       COALESCE(s.enabled, FALSE) AS sso_enabled,
                       COALESCE(s.force_sso, FALSE) AS force_sso,
                       COALESCE(s.protocol, 'OIDC') AS protocol,
                       COALESCE(s.sso_provider, 'CUSTOM') AS sso_provider,
                       COALESCE(s.corporate_cidr_ips, '[]'::jsonb)::text AS corporate_cidr_ips
                FROM tenant_domains td
                JOIN tenants t ON t.id = td.tenant_id
                LEFT JOIN tenant_sso_configs s ON s.tenant_id = td.tenant_id
                WHERE lower(td.domain_name) = lower(?)
                  AND (td.is_verified = TRUE OR td.verification_status IN ('ACTIVE', 'VERIFIED'))
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(mapHrdRow(rs)) : Optional.empty(),
                domainName.trim());
    }

    public List<HrdTenantRow> listEnabledSsoWithCorporateCidrs() {
        return jdbc.query(
                """
                SELECT t.id AS tenant_id, t.name AS company_name,
                       s.enabled AS sso_enabled, s.force_sso,
                       COALESCE(s.protocol, 'OIDC') AS protocol,
                       COALESCE(s.sso_provider, 'CUSTOM') AS sso_provider,
                       COALESCE(s.corporate_cidr_ips, '[]'::jsonb)::text AS corporate_cidr_ips
                FROM tenant_sso_configs s
                JOIN tenants t ON t.id = s.tenant_id
                WHERE s.enabled = TRUE
                  AND s.corporate_cidr_ips IS NOT NULL
                  AND s.corporate_cidr_ips <> '[]'::jsonb
                """,
                (rs, rowNum) -> mapHrdRow(rs));
    }

    private static HrdTenantRow mapHrdRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new HrdTenantRow(
                UUID.fromString(rs.getString("tenant_id")),
                rs.getString("company_name"),
                rs.getBoolean("sso_enabled"),
                rs.getBoolean("force_sso"),
                rs.getString("protocol"),
                rs.getString("sso_provider"),
                parseStringList(rs.getString("corporate_cidr_ips")));
    }

    private static List<String> parseStringList(String raw) {
        if (raw == null || raw.isBlank() || "[]".equals(raw.trim())) {
            return List.of();
        }
        try {
            List<String> parsed = JSON.readValue(raw, new TypeReference<List<String>>() { });
            return parsed != null ? parsed : List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    public void insertOauthCallbackState(String state, UUID tenantId, String provider, String payloadJson,
                                         java.time.Instant expiresAt) {
        jdbc.update("""
                INSERT INTO oauth_callback_states (state, tenant_id, provider, payload, expires_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (state) DO UPDATE
                SET tenant_id = EXCLUDED.tenant_id,
                    provider = EXCLUDED.provider,
                    payload = EXCLUDED.payload,
                    expires_at = EXCLUDED.expires_at
                """,
                state, tenantId, provider, payloadJson, java.sql.Timestamp.from(expiresAt));
    }

    public Optional<OauthStateRow> consumeOauthCallbackState(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        Optional<OauthStateRow> row = jdbc.query(
                """
                SELECT state, tenant_id, provider, payload::text AS payload, expires_at
                FROM oauth_callback_states
                WHERE state = ?
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new OauthStateRow(
                            rs.getString("state"),
                            UUID.fromString(rs.getString("tenant_id")),
                            rs.getString("provider"),
                            rs.getString("payload"),
                            rs.getTimestamp("expires_at").toInstant()));
                },
                state.trim());
        row.ifPresent(r -> jdbc.update("DELETE FROM oauth_callback_states WHERE state = ?", r.state()));
        return row;
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
        List<InvoiceBootstrapRow> matches = jdbc.query(
                "SELECT id, tenant_id, number, status FROM invoices WHERE number = ?",
                (rs, rowNum) -> new InvoiceBootstrapRow(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("tenant_id")),
                        rs.getString("number"),
                        rs.getString("status")),
                trimmed);
        if (matches.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    public List<MeshPartnerRow> listConnectedMeshPartnersForBuyer(UUID buyerTenantId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, partner_tenant_id, supplier_id, customer_id, connection_status
                FROM tenant_mesh_partners
                WHERE tenant_id = ?
                  AND connection_status = 'CONNECTED'
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> mapMeshPartner(rs),
                buyerTenantId);
    }

    public List<MeshRelationshipRow> listMeshRelationshipsForTenant(UUID tenantId) {
        return jdbc.query(
                """
                SELECT p.id, p.tenant_id, p.partner_tenant_id, p.supplier_id, p.customer_id,
                       p.connection_status,
                       CASE WHEN p.tenant_id = ? THEN seller.name ELSE buyer.name END AS partner_name
                FROM tenant_mesh_partners p
                JOIN tenants buyer ON buyer.id = p.tenant_id
                JOIN tenants seller ON seller.id = p.partner_tenant_id
                WHERE (p.tenant_id = ? OR p.partner_tenant_id = ?)
                  AND p.connection_status IN ('PENDING', 'REQUESTED', 'CONNECTED')
                ORDER BY p.updated_at DESC
                """,
                (rs, rowNum) -> new MeshRelationshipRow(
                        uuidOrNull(rs.getString("id")),
                        uuidOrNull(rs.getString("tenant_id")),
                        uuidOrNull(rs.getString("partner_tenant_id")),
                        uuidOrNull(rs.getString("supplier_id")),
                        uuidOrNull(rs.getString("customer_id")),
                        rs.getString("connection_status"),
                        rs.getString("partner_name")),
                tenantId, tenantId, tenantId);
    }

    public Optional<MeshPartnerRow> findMeshPartnerById(UUID meshPartnerId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, partner_tenant_id, supplier_id, customer_id, connection_status
                FROM tenant_mesh_partners
                WHERE id = ?
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(mapMeshPartner(rs)) : Optional.empty(),
                meshPartnerId);
    }

    public Optional<MeshPartnerRow> findMeshByBuyerAndSeller(UUID buyerTenantId, UUID sellerTenantId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, partner_tenant_id, supplier_id, customer_id, connection_status
                FROM tenant_mesh_partners
                WHERE tenant_id = ?
                  AND partner_tenant_id = ?
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(mapMeshPartner(rs)) : Optional.empty(),
                buyerTenantId, sellerTenantId);
    }

    public List<PublishedMeshListingRow> listPublishedMeshListings(UUID excludeTenantId) {
        return jdbc.query(
                """
                SELECT mcl.variant_id, mcl.tenant_id,
                       COALESCE(p.name, pv.sku) AS product_name,
                       t.name AS seller_name,
                       (SELECT pm.url FROM product_media pm
                         WHERE pm.tenant_id = mcl.tenant_id
                           AND pm.variant_id = mcl.variant_id
                         ORDER BY pm.is_primary DESC, pm.sort_order ASC
                         LIMIT 1) AS image_url
                FROM mesh_catalog_listings mcl
                JOIN product_variants pv ON pv.id = mcl.variant_id AND pv.tenant_id = mcl.tenant_id
                LEFT JOIN products p ON p.id = pv.product_id AND p.tenant_id = pv.tenant_id
                JOIN tenants t ON t.id = mcl.tenant_id
                WHERE mcl.published = TRUE
                  AND mcl.tenant_id <> ?
                ORDER BY product_name, pv.sku
                """,
                (rs, rowNum) -> new PublishedMeshListingRow(
                        uuidOrNull(rs.getString("variant_id")),
                        uuidOrNull(rs.getString("tenant_id")),
                        rs.getString("product_name"),
                        rs.getString("seller_name"),
                        rs.getString("image_url")),
                excludeTenantId);
    }

    public Optional<PublishedMeshListingRow> findPublishedListingByVariant(UUID variantId) {
        return jdbc.query(
                """
                SELECT mcl.variant_id, mcl.tenant_id,
                       COALESCE(p.name, pv.sku) AS product_name,
                       t.name AS seller_name,
                       NULL AS image_url
                FROM mesh_catalog_listings mcl
                JOIN product_variants pv ON pv.id = mcl.variant_id AND pv.tenant_id = mcl.tenant_id
                LEFT JOIN products p ON p.id = pv.product_id AND p.tenant_id = pv.tenant_id
                JOIN tenants t ON t.id = mcl.tenant_id
                WHERE mcl.variant_id = ?
                  AND mcl.published = TRUE
                LIMIT 1
                """,
                rs -> rs.next()
                        ? Optional.of(new PublishedMeshListingRow(
                        uuidOrNull(rs.getString("variant_id")),
                        uuidOrNull(rs.getString("tenant_id")),
                        rs.getString("product_name"),
                        rs.getString("seller_name"),
                        rs.getString("image_url")))
                        : Optional.empty(),
                variantId);
    }

    public Optional<PartnerCatalogSku> findPublishedPartnerSkuMatch(
            UUID partnerTenantId, String sku, String barcode) {
        return jdbc.query(
                """
                SELECT pv.id AS variant_id, pv.sku, COALESCE(p.name, pv.sku) AS product_name
                FROM mesh_catalog_listings mcl
                JOIN product_variants pv ON pv.id = mcl.variant_id AND pv.tenant_id = mcl.tenant_id
                LEFT JOIN products p ON p.id = pv.product_id AND p.tenant_id = pv.tenant_id
                WHERE mcl.tenant_id = ?
                  AND mcl.published = TRUE
                  AND (
                        LOWER(pv.sku) = LOWER(?)
                        OR (? IS NOT NULL AND ? <> '' AND LOWER(COALESCE(pv.barcode, '')) = LOWER(?))
                      )
                LIMIT 1
                """,
                rs -> rs.next()
                        ? Optional.of(new PartnerCatalogSku(
                        UUID.fromString(rs.getString("variant_id")),
                        rs.getString("sku"),
                        rs.getString("product_name")))
                        : Optional.empty(),
                partnerTenantId, sku, barcode, barcode, barcode);
    }

    public List<PartnerCatalogSku> listPartnerCatalogSkus(UUID partnerTenantId) {
        return jdbc.query(
                """
                SELECT pv.id AS variant_id, pv.sku, COALESCE(p.name, pv.sku) AS product_name
                FROM product_variants pv
                LEFT JOIN products p ON p.id = pv.product_id AND p.tenant_id = pv.tenant_id
                WHERE pv.tenant_id = ?
                  AND pv.sku NOT LIKE 'MESH-PENDING-%'
                ORDER BY pv.sku
                """,
                (rs, rowNum) -> new PartnerCatalogSku(
                        UUID.fromString(rs.getString("variant_id")),
                        rs.getString("sku"),
                        rs.getString("product_name")),
                partnerTenantId);
    }

    /**
     * Authoritative mesh pairing write (SECURITY DEFINER; safe across RLS boundaries).
     */
    public UUID upsertMeshPartner(UUID buyerTenantId, UUID sellerTenantId,
                                  UUID supplierId, UUID customerId, String status) {
        return jdbc.queryForObject(
                "SELECT bootstrap_upsert_mesh_partner(?, ?, ?, ?, ?)",
                UUID.class,
                buyerTenantId, sellerTenantId, supplierId, customerId, status);
    }

    /**
     * Cross-tenant mesh lookup (bypasses RLS): buyer tenant + supplier → CONNECTED partner.
     */
    public Optional<MeshPartnerRow> findConnectedMeshByBuyerSupplier(UUID buyerTenantId, UUID supplierId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, partner_tenant_id, supplier_id, customer_id, connection_status
                FROM tenant_mesh_partners
                WHERE tenant_id = ?
                  AND supplier_id = ?
                  AND connection_status = 'CONNECTED'
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(mapMeshPartner(rs)) : Optional.empty(),
                buyerTenantId, supplierId);
    }

    /**
     * Cross-tenant mesh lookup (bypasses RLS): seller tenant + customer → CONNECTED partner.
     */
    public Optional<MeshPartnerRow> findConnectedMeshBySellerCustomer(UUID sellerTenantId, UUID customerId) {
        return jdbc.query(
                """
                SELECT id, tenant_id, partner_tenant_id, supplier_id, customer_id, connection_status
                FROM tenant_mesh_partners
                WHERE partner_tenant_id = ?
                  AND customer_id = ?
                  AND connection_status = 'CONNECTED'
                LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(mapMeshPartner(rs)) : Optional.empty(),
                sellerTenantId, customerId);
    }

    private static MeshPartnerRow mapMeshPartner(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MeshPartnerRow(
                uuidOrNull(rs.getString("id")),
                uuidOrNull(rs.getString("tenant_id")),
                uuidOrNull(rs.getString("partner_tenant_id")),
                uuidOrNull(rs.getString("supplier_id")),
                uuidOrNull(rs.getString("customer_id")),
                rs.getString("connection_status"));
    }

    private static UUID uuidOrNull(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    public record MeshPartnerRow(
            UUID id,
            UUID tenantId,
            UUID partnerTenantId,
            UUID supplierId,
            UUID customerId,
            String connectionStatus) {
    }

    public record MeshRelationshipRow(
            UUID id,
            UUID tenantId,
            UUID partnerTenantId,
            UUID supplierId,
            UUID customerId,
            String connectionStatus,
            String partnerName) {
    }

    public record PublishedMeshListingRow(
            UUID variantId,
            UUID sellerTenantId,
            String productName,
            String sellerName,
            String imageUrl) {
    }

    public record PartnerCatalogSku(UUID variantId, String sku, String productName) {
    }

    public record UserAuthRow(UUID id, String passwordHash, String status) {}

    public record UserAuthWithTenantRow(
            UUID id,
            UUID tenantId,
            String passwordHash,
            String status) {}

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

    public record DomainSsoRow(UUID tenantId, String issuerUrl, String clientId,
                               boolean enabled, boolean forceSso, String protocol) {
    }

    public record HrdTenantRow(
            UUID tenantId,
            String companyName,
            boolean ssoEnabled,
            boolean forceSso,
            String protocol,
            String ssoProvider,
            List<String> corporateCidrIps
    ) {
    }

    public record OauthStateRow(String state, UUID tenantId, String provider,
                                String payloadJson, java.time.Instant expiresAt) {
    }

    public void upsertCurrencyRate(String fromCurrency, String toCurrency, java.math.BigDecimal rate, java.time.Instant asOf) {
        jdbc.update("""
                INSERT INTO currency_rates (id, from_currency, to_currency, rate, as_of, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (from_currency, to_currency)
                DO UPDATE SET rate = EXCLUDED.rate, as_of = EXCLUDED.as_of, updated_at = NOW()
                """, fromCurrency, toCurrency, rate, java.sql.Timestamp.from(asOf));
    }

    public Optional<UUID> findTenantIdByStripeCustomerId(String stripeCustomerId) {
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                "SELECT id FROM tenants WHERE stripe_customer_id = ? LIMIT 1",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                stripeCustomerId.trim());
    }

    public int updateTenantSubscriptionStatus(UUID tenantId, String subscriptionStatus) {
        return jdbc.update("""
                UPDATE tenants
                SET subscription_status = ?, updated_at = NOW()
                WHERE id = ?
                """, subscriptionStatus, tenantId);
    }

    /**
     * Pre-auth invitation lookup (bypasses RLS) — accept flow has no tenant context yet.
     */
    public Optional<InvitationBootstrapRow> findOpenInvitationByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query(
                """
                SELECT id, tenant_id, email, role_id, customer_id, supplier_id, expires_at, accepted_at
                FROM invitations
                WHERE token_hash = ?
                LIMIT 1
                """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new InvitationBootstrapRow(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            rs.getString("email"),
                            UUID.fromString(rs.getString("role_id")),
                            rs.getString("customer_id") != null
                                    ? UUID.fromString(rs.getString("customer_id")) : null,
                            rs.getString("supplier_id") != null
                                    ? UUID.fromString(rs.getString("supplier_id")) : null,
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("accepted_at") != null
                                    ? rs.getTimestamp("accepted_at").toInstant() : null));
                },
                tokenHash.trim());
    }

    public void markInvitationAccepted(UUID invitationId) {
        jdbc.update("""
                UPDATE invitations
                SET accepted_at = NOW(), updated_at = NOW()
                WHERE id = ?
                """, invitationId);
    }

    public List<TenantWithSubscriptionRow> listTenantsWithSubscriptions() {
        return jdbc.query(
                """
                SELECT t.id, t.name, t.slug, t.status,
                       COALESCE(s.tier, 'BASIC') AS tier,
                       COALESCE(s.enabled_modules::text, '["CORE"]') AS enabled_modules
                FROM tenants t
                LEFT JOIN tenant_subscriptions s ON s.tenant_id = t.id
                ORDER BY t.name
                """,
                (rs, rowNum) -> new TenantWithSubscriptionRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("status"),
                        rs.getString("tier"),
                        rs.getString("enabled_modules")));
    }

    public Optional<TenantNameSlugStatusRow> findTenantNameSlugStatus(UUID tenantId) {
        return jdbc.query(
                "SELECT id, name, slug, status FROM tenants WHERE id = ?",
                rs -> rs.next()
                        ? Optional.of(new TenantNameSlugStatusRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("status")))
                        : Optional.empty(),
                tenantId);
    }

    public Optional<TenantSubscriptionRow> findTenantSubscription(UUID tenantId) {
        return jdbc.query(
                """
                SELECT t.id, t.name, t.slug, t.status,
                       COALESCE(s.tier, 'BASIC') AS tier,
                       COALESCE(s.enabled_modules::text, '["CORE"]') AS enabled_modules
                FROM tenants t
                LEFT JOIN tenant_subscriptions s ON s.tenant_id = t.id
                WHERE t.id = ?
                """,
                rs -> rs.next()
                        ? Optional.of(new TenantSubscriptionRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("status"),
                        rs.getString("tier"),
                        rs.getString("enabled_modules")))
                        : Optional.empty(),
                tenantId);
    }

    public void insertTenantSubscription(UUID tenantId, String tier, String enabledModulesJson) {
        jdbc.update("""
                INSERT INTO tenant_subscriptions (tenant_id, tier, enabled_modules, updated_at)
                VALUES (?, ?, CAST(? AS jsonb), NOW())
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, tier, enabledModulesJson);
    }

    public Optional<TenantSubscriptionRow> upsertTenantEnabledModules(UUID tenantId, String enabledModulesJson) {
        jdbc.update("""
                INSERT INTO tenant_subscriptions (tenant_id, tier, enabled_modules, updated_at)
                VALUES (?, 'BASIC', CAST(? AS jsonb), NOW())
                ON CONFLICT (tenant_id) DO UPDATE
                SET enabled_modules = EXCLUDED.enabled_modules,
                    updated_at = NOW()
                """, tenantId, enabledModulesJson);
        return findTenantSubscription(tenantId);
    }

    public Optional<TenantSubscriptionRow> upsertTenantTierAndModules(
            UUID tenantId, String tier, String enabledModulesJson) {
        jdbc.update("""
                INSERT INTO tenant_subscriptions (tenant_id, tier, enabled_modules, updated_at)
                VALUES (?, ?, CAST(? AS jsonb), NOW())
                ON CONFLICT (tenant_id) DO UPDATE
                SET tier = EXCLUDED.tier,
                    enabled_modules = EXCLUDED.enabled_modules,
                    updated_at = NOW()
                """, tenantId, tier, enabledModulesJson);
        return findTenantSubscription(tenantId);
    }

    public record TenantWithSubscriptionRow(
            UUID tenantId,
            String name,
            String slug,
            String status,
            String tier,
            String enabledModulesJson
    ) {
    }

    public record TenantNameSlugStatusRow(
            UUID tenantId,
            String name,
            String slug,
            String status
    ) {
    }

    public record TenantSubscriptionRow(
            UUID tenantId,
            String name,
            String slug,
            String status,
            String tier,
            String enabledModulesJson
    ) {
    }

    public record InvitationBootstrapRow(
            UUID id,
            UUID tenantId,
            String email,
            UUID roleId,
            UUID customerId,
            UUID supplierId,
            java.time.Instant expiresAt,
            java.time.Instant acceptedAt
    ) {
    }
}

