-- Training sandbox shadow tenants (optional invsys-training module).
-- Maps a live tenant to an isolated clone used when X-Training-Mode: true.

CREATE TABLE training_sandbox_bindings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_tenant_id    UUID NOT NULL,
    sandbox_tenant_id   UUID NOT NULL UNIQUE,
    created_by          UUID,
    label               TEXT NOT NULL DEFAULT 'flight-simulator',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ,
    CONSTRAINT training_sandbox_source_unique UNIQUE (source_tenant_id, label)
);

CREATE INDEX idx_training_sandbox_source
    ON training_sandbox_bindings (source_tenant_id)
    WHERE active;

-- No RLS: platform table looked up under bootstrap when swapping TenantContext.
GRANT SELECT, INSERT, UPDATE, DELETE ON training_sandbox_bindings TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON training_sandbox_bindings TO app_user;

COMMENT ON TABLE training_sandbox_bindings IS
    'Maps production tenants to disposable training shadow tenants for Flight Simulator mode';
