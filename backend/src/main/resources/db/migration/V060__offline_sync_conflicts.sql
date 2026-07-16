-- V060: Server-side DLQ for failed offline mutation replays

CREATE TABLE offline_sync_conflicts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message   TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED', 'RETRY_REQUESTED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_offline_sync_conflicts_tenant_status
    ON offline_sync_conflicts (tenant_id, status, created_at DESC);

CREATE TRIGGER offline_sync_conflicts_updated_at BEFORE UPDATE ON offline_sync_conflicts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE offline_sync_conflicts ENABLE ROW LEVEL SECURITY;
ALTER TABLE offline_sync_conflicts FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON offline_sync_conflicts
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON offline_sync_conflicts TO app_user;
GRANT SELECT ON offline_sync_conflicts TO app_owner;
