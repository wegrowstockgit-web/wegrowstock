-- Track 0: platform foundations (outbox retry, credential vault, sync logs, rate limits, costing, webhook fix)

-- 0.1 Outbox dispatcher columns
ALTER TABLE outbox_events ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN next_attempt_at TIMESTAMPTZ;
ALTER TABLE outbox_events ADD COLUMN last_error TEXT;
ALTER TABLE outbox_events ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_outbox_dispatch ON outbox_events(created_at)
    WHERE published_at IS NULL AND status = 'PENDING';

-- 0.2 Encrypted credential vault
CREATE TABLE integration_credentials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    system          VARCHAR(50) NOT NULL,
    ciphertext      BYTEA NOT NULL,
    key_version     INT NOT NULL DEFAULT 1,
    status          VARCHAR(30) NOT NULL DEFAULT 'CONNECTED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, system)
);

CREATE TRIGGER integration_credentials_updated_at BEFORE UPDATE ON integration_credentials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 0.3 Integration sync logs
CREATE TABLE integration_sync_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    system          VARCHAR(50) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count     INT NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sync_logs_tenant_system_status ON integration_sync_logs(tenant_id, system, status);

CREATE TRIGGER integration_sync_logs_updated_at BEFORE UPDATE ON integration_sync_logs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 0.3 Per-tenant rate-limit buckets
CREATE TABLE integration_rate_buckets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    system              VARCHAR(50) NOT NULL,
    tokens_remaining    NUMERIC(19,4) NOT NULL DEFAULT 0,
    window_start        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, system)
);

CREATE TRIGGER integration_rate_buckets_updated_at BEFORE UPDATE ON integration_rate_buckets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 0.4 Costing layer
ALTER TABLE inventory_ledger ADD COLUMN unit_cost NUMERIC(19,4) NULL;

ALTER TABLE product_variants ADD COLUMN avg_cost NUMERIC(19,4) NOT NULL DEFAULT 0;
ALTER TABLE product_variants ADD COLUMN external_sync_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- M8: tenant-scoped webhook idempotency (tenant_id nullable → partial unique indexes)
ALTER TABLE webhook_events DROP CONSTRAINT IF EXISTS webhook_events_source_external_event_id_key;

CREATE UNIQUE INDEX uq_webhook_tenant_source_event
    ON webhook_events(tenant_id, source, external_event_id)
    WHERE tenant_id IS NOT NULL;

CREATE UNIQUE INDEX uq_webhook_unresolved_source_event
    ON webhook_events(source, external_event_id)
    WHERE tenant_id IS NULL;

-- RLS on new tenant-scoped tables (V009 pattern)
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'integration_credentials', 'integration_sync_logs', 'integration_rate_buckets'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true)::uuid)',
            t
        );
    END LOOP;
END $$;

-- Bootstrap policies for outbox dispatcher (cross-tenant worker via app_owner)
CREATE POLICY bootstrap_outbox_read ON outbox_events
    FOR SELECT TO app_owner
    USING (true);

CREATE POLICY bootstrap_outbox_update ON outbox_events
    FOR UPDATE TO app_owner
    USING (true)
    WITH CHECK (true);

GRANT SELECT, UPDATE ON outbox_events TO app_owner;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    integration_credentials, integration_sync_logs, integration_rate_buckets
TO app_user;
