-- Platform alerts for integration health monitoring (tenant-scoped, RLS)

CREATE TABLE platform_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    alert_type      VARCHAR(80) NOT NULL,
    severity        VARCHAR(20) NOT NULL DEFAULT 'WARNING'
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    source_system   VARCHAR(50),
    title           VARCHAR(255) NOT NULL,
    details         JSONB NOT NULL DEFAULT '{}'::jsonb,
    acknowledged_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_platform_alerts_tenant_open
    ON platform_alerts(tenant_id, created_at DESC)
    WHERE acknowledged_at IS NULL;

CREATE TRIGGER platform_alerts_updated_at BEFORE UPDATE ON platform_alerts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE platform_alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE platform_alerts FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON platform_alerts
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON platform_alerts TO app_user;
