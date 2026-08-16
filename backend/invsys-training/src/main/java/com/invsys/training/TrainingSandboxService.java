package com.invsys.training;

import com.invsys.core.tenancy.BootstrapJdbc;
import com.invsys.core.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

/**
 * Creates / resolves disposable shadow tenants for Flight Simulator mode.
 * Uses the bootstrap (app_owner) DataSource so platform bindings are visible without RLS.
 */
@Service
@ConditionalOnProperty(name = "invsys.features.training.enabled", havingValue = "true", matchIfMissing = true)
public class TrainingSandboxService {

    private final JdbcTemplate jdbcTemplate;
    private final BootstrapJdbc bootstrapJdbc;

    public TrainingSandboxService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource,
                                  BootstrapJdbc bootstrapJdbc) {
        this.jdbcTemplate = new JdbcTemplate(bootstrapDataSource);
        this.bootstrapJdbc = bootstrapJdbc;
    }

    @Transactional
    public UUID resolveOrCreateSandboxTenant(UUID sourceTenantId, UUID createdBy) {
        UUID existing = jdbcTemplate.query(
                """
                        SELECT sandbox_tenant_id
                          FROM training_sandbox_bindings
                         WHERE source_tenant_id = ?
                           AND label = 'flight-simulator'
                           AND active = TRUE
                         LIMIT 1
                        """,
                rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                sourceTenantId);
        if (existing != null) {
            return existing;
        }
        UUID sandboxId = UUID.randomUUID();
        bootstrapJdbc.insertProvisionedTenant(
                sandboxId, "Training Sandbox", "train-" + sandboxId.toString().substring(0, 8));
        jdbcTemplate.update("""
                INSERT INTO training_sandbox_bindings (
                    source_tenant_id, sandbox_tenant_id, created_by, label, active
                ) VALUES (?, ?, ?, 'flight-simulator', TRUE)
                ON CONFLICT (source_tenant_id, label) DO UPDATE
                   SET sandbox_tenant_id = EXCLUDED.sandbox_tenant_id,
                       active = TRUE
                """,
                sourceTenantId,
                sandboxId,
                createdBy);
        return sandboxId;
    }

    public boolean isCurrentTenantSandbox() {
        UUID current = TenantContext.getTenantId().orElse(null);
        if (current == null) {
            return false;
        }
        Boolean found = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS(
                            SELECT 1 FROM training_sandbox_bindings
                             WHERE sandbox_tenant_id = ? AND active = TRUE
                        )
                        """,
                Boolean.class,
                current);
        return Boolean.TRUE.equals(found);
    }
}
