package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.tenancy.BootstrapJdbc;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Provisions disposable UAT sandbox tenants (mirrors TrainingSandboxService without depending on invsys-training).
 */
@Service
public class AdminSandboxProvisioningService {

    private final JdbcTemplate jdbc;
    private final BootstrapJdbc bootstrapJdbc;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminSandboxProvisioningService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                                           BootstrapJdbc bootstrapJdbc) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.bootstrapJdbc = bootstrapJdbc;
    }

    @Transactional
    public SandboxCredentials cloneSandbox(UUID sourceTenantId) {
        BootstrapJdbc.TenantSubscriptionRow source = bootstrapJdbc.findTenantSubscription(sourceTenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        UUID existingSandbox = jdbc.query(
                """
                SELECT sandbox_tenant_id FROM platform_sandbox_credentials
                WHERE source_tenant_id = ?
                LIMIT 1
                """,
                rs -> rs.next() ? UUID.fromString(rs.getString(1)) : null,
                sourceTenantId);

        UUID sandboxId;
        String slug;
        if (existingSandbox != null) {
            sandboxId = existingSandbox;
            slug = bootstrapJdbc.findTenantNameSlugStatus(sandboxId)
                    .map(BootstrapJdbc.TenantNameSlugStatusRow::slug)
                    .orElse("uat-" + sandboxId.toString().substring(0, 8));
        } else {
            sandboxId = UUID.randomUUID();
            slug = "uat-" + sandboxId.toString().substring(0, 8);
            jdbc.update("""
                    INSERT INTO tenants (id, name, slug, status, subscription_status, created_at, updated_at)
                    VALUES (?, ?, ?, 'ACTIVE', 'ACTIVE', NOW(), NOW())
                    """,
                    sandboxId,
                    source.name() + " (UAT)",
                    slug);
            bootstrapJdbc.upsertTenantTierAndModules(
                    sandboxId, source.tier(), source.enabledModulesJson());
        }

        String apiKey = "sk_uat_" + randomToken(24);
        String hash = sha256Hex(apiKey);
        String hint = apiKey.substring(Math.max(0, apiKey.length() - 4));
        UUID createdBy = currentAdminId();

        jdbc.update("""
                INSERT INTO platform_sandbox_credentials (
                    id, source_tenant_id, sandbox_tenant_id, api_key_hash, api_key_hint, created_at, created_by
                ) VALUES (?, ?, ?, ?, ?, NOW(), ?)
                ON CONFLICT (source_tenant_id) DO UPDATE SET
                    sandbox_tenant_id = EXCLUDED.sandbox_tenant_id,
                    api_key_hash = EXCLUDED.api_key_hash,
                    api_key_hint = EXCLUDED.api_key_hint,
                    created_at = NOW(),
                    created_by = EXCLUDED.created_by
                """,
                UUID.randomUUID(),
                sourceTenantId,
                sandboxId,
                hash,
                hint,
                createdBy);

        return new SandboxCredentials(sourceTenantId, sandboxId, slug, apiKey, hint);
    }

    private static UUID currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        secureRandom.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash API key", e);
        }
    }

    public record SandboxCredentials(
            UUID sourceTenantId,
            UUID sandboxTenantId,
            String sandboxSlug,
            String apiKey,
            String apiKeyHint
    ) {
    }
}
