-- V075: CQRS read model for dashboard KPIs (decouple OLTP aggregates from /dashboard/stats)

CREATE TABLE dashboard_kpi_snapshots (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    stock_value             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency                VARCHAR(8) NOT NULL DEFAULT 'USD',
    low_stock_count         BIGINT NOT NULL DEFAULT 0,
    open_orders_count       BIGINT NOT NULL DEFAULT 0,
    unpaid_invoices_count   BIGINT NOT NULL DEFAULT 0,
    source_event_type       VARCHAR(80),
    refreshed_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT dashboard_kpi_snapshots_tenant_unique UNIQUE (tenant_id)
);

CREATE TRIGGER dashboard_kpi_snapshots_updated_at BEFORE UPDATE ON dashboard_kpi_snapshots
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE dashboard_kpi_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE dashboard_kpi_snapshots FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON dashboard_kpi_snapshots
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON dashboard_kpi_snapshots TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON dashboard_kpi_snapshots TO app_owner;

COMMENT ON TABLE dashboard_kpi_snapshots IS
    'CQRS read model for DashboardStatsResponse; refreshed asynchronously from outbox events';
