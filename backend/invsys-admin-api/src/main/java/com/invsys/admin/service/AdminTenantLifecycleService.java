package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import com.invsys.core.service.FeatureFlagService;
import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.ratelimit.DistributedRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminTenantLifecycleService {

    private static final Set<String> ALLOWED = Set.of("ACTIVE", "SUSPENDED");
    private static final Set<String> PURGE_SKIP_TABLES = Set.of(
            "tenants", "users", "audit_log", "tenant_subscriptions", "platform_audit_logs");
    public static final String EVENT_TENANT_S3_PURGE = "TENANT_S3_PURGE";

    private static final Logger log = LoggerFactory.getLogger(AdminTenantLifecycleService.class);

    private final BootstrapJdbc bootstrapJdbc;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate bootstrapTx;
    private final DistributedRateLimiter distributedRateLimiter;
    private final FeatureFlagService featureFlagService;
    private final StringRedisTemplate redis;

    public AdminTenantLifecycleService(BootstrapJdbc bootstrapJdbc,
                                       @Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                                       DistributedRateLimiter distributedRateLimiter,
                                       FeatureFlagService featureFlagService,
                                       ObjectProvider<StringRedisTemplate> redisProvider) {
        this.bootstrapJdbc = bootstrapJdbc;
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
        this.bootstrapTx = new TransactionTemplate(new DataSourceTransactionManager(bootstrapDataSource));
        this.distributedRateLimiter = distributedRateLimiter;
        this.featureFlagService = featureFlagService;
        this.redis = redisProvider.getIfAvailable();
    }

    @Transactional
    public TenantStatusView updateStatus(UUID tenantId, String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
                    "status must be ACTIVE or SUSPENDED");
        }

        BootstrapJdbc.TenantNameSlugStatusRow tenant = bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

        bootstrapJdbc.updateTenantStatus(tenantId, normalized);
        return new TenantStatusView(tenant.tenantId(), tenant.name(), tenant.slug(), normalized);
    }

    /**
     * GDPR / offboarding saga: soft-lock, anonymize PII, cascade-delete operational rows,
     * enqueue S3 prefix purge, and evict Redis throttle / flag / session keys.
     * Uses the bootstrap DataSource transaction so PostgreSQL SAVEPOINTs are legal
     * (the default {@code @Transactional} manager is not bound to app_owner).
     */
    public TenantPurgeView purgeTenantData(UUID tenantId) {
        return bootstrapTx.execute(status -> {
            BootstrapJdbc.TenantNameSlugStatusRow tenant = bootstrapJdbc.findTenantNameSlugStatus(tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));

            bootstrapJdbc.updateTenantStatus(tenantId, "PURGED");
            anonymizeIdentities(tenantId);
            int deleted = cascadeDeleteOperational(tenantId);
            enqueueS3Purge(tenantId);
            evictCaches(tenantId);

            return new TenantPurgeView(tenant.tenantId(), tenant.slug(), "PURGED", deleted);
        });
    }

    private void anonymizeIdentities(UUID tenantId) {
        jdbc.update("""
                UPDATE users
                   SET email = 'purged-' || id::text || '@invalid.local',
                       display_name = 'Purged user',
                       password_hash = '!',
                       phone = NULL,
                       address_line1 = NULL,
                       address_line2 = NULL,
                       address_city = NULL,
                       address_region = NULL,
                       address_postal_code = NULL,
                       address_country = NULL,
                       avatar_url = NULL,
                       terminal_pin_hash = NULL,
                       status = 'INACTIVE',
                       updated_at = NOW()
                 WHERE tenant_id = ?
                """, tenantId);
        jdbc.update("DELETE FROM refresh_tokens WHERE tenant_id = ?", tenantId);
        jdbc.update("""
                UPDATE audit_log
                   SET diff = jsonb_strip_nulls(
                         COALESCE(diff, '{}'::jsonb)
                         - 'email' - 'ip' - 'location' - 'phone' - 'displayName')
                 WHERE tenant_id = ?
                """, tenantId);
    }

    private int cascadeDeleteOperational(UUID tenantId) {
        List<String> tables = jdbc.query(
                """
                SELECT c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                  JOIN pg_attribute a ON a.attrelid = c.oid
                 WHERE n.nspname = 'public'
                   AND c.relkind = 'r'
                   AND NOT c.relispartition
                   AND a.attname = 'tenant_id'
                   AND NOT a.attisdropped
                 ORDER BY c.relname
                """,
                (rs, rowNum) -> rs.getString(1));
        int deleted = 0;
        for (int pass = 0; pass < 12; pass++) {
            int passCount = 0;
            int index = 0;
            for (String table : tables) {
                if (PURGE_SKIP_TABLES.contains(table)) {
                    continue;
                }
                String savepoint = "purge_sp_" + pass + "_" + index++;
                jdbc.execute("SAVEPOINT " + savepoint);
                try {
                    passCount += jdbc.update(
                            "DELETE FROM " + quoteIdent(table) + " WHERE tenant_id = ?", tenantId);
                    jdbc.execute("RELEASE SAVEPOINT " + savepoint);
                } catch (org.springframework.dao.DataAccessException ex) {
                    jdbc.execute("ROLLBACK TO SAVEPOINT " + savepoint);
                    log.debug("Purge skip {} on pass {}: {}", table, pass, ex.getMessage());
                }
            }
            deleted += passCount;
            if (passCount == 0) {
                break;
            }
        }
        return deleted;
    }

    private void enqueueS3Purge(UUID tenantId) {
        jdbc.update("""
                INSERT INTO outbox_events (
                    id, tenant_id, aggregate_type, aggregate_id, event_type, payload,
                    status, retry_count, created_at, updated_at)
                VALUES (?, ?, 'TENANT', ?, ?, CAST(? AS jsonb), 'PENDING', 0, NOW(), NOW())
                """,
                UUID.randomUUID(),
                tenantId,
                tenantId,
                EVENT_TENANT_S3_PURGE,
                "{\"prefix\":\"" + tenantId + "/\"}");
    }

    private void evictCaches(UUID tenantId) {
        distributedRateLimiter.evictTenant(tenantId);
        featureFlagService.evictTenant(tenantId);
        if (redis == null) {
            return;
        }
        String id = tenantId.toString();
        List<String> patterns = List.of(
                "tenant:throttle:" + id,
                "invsys:rate-multiplier:" + id,
                "invsys:tenant-settings:" + id,
                "flags:*:" + id,
                "rate:" + id + ":*");
        try {
            Set<String> keys = new HashSet<>();
            for (String pattern : patterns) {
                if (!pattern.contains("*")) {
                    keys.add(pattern);
                    continue;
                }
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(256).build();
                try (Cursor<String> cursor = redis.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(cursor.next());
                    }
                }
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (RuntimeException ex) {
            log.warn("Redis eviction during tenant purge failed tenant={}: {}", tenantId, ex.getMessage());
        }
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "") + "\"";
    }

    public record TenantStatusView(UUID tenantId, String name, String slug, String status) {
    }

    public record TenantPurgeView(UUID tenantId, String slug, String status, int deletedRows) {
    }
}
