-- Track 21-23: Manufacturing labor, timesheets, disassembly, auto-assemble BOMs

ALTER TABLE boms ADD COLUMN IF NOT EXISTS auto_assemble BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE manufacturing_operations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    default_hourly_rate NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (default_hourly_rate >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

CREATE TRIGGER manufacturing_operations_updated_at BEFORE UPDATE ON manufacturing_operations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE team_labor_rates (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    hourly_rate  NUMERIC(19,4) NOT NULL CHECK (hourly_rate >= 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id)
);

CREATE TRIGGER team_labor_rates_updated_at BEFORE UPDATE ON team_labor_rates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE bom_operations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    bom_id           UUID NOT NULL REFERENCES boms(id) ON DELETE CASCADE,
    operation_id     UUID NOT NULL REFERENCES manufacturing_operations(id) ON DELETE CASCADE,
    estimated_hours  NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (estimated_hours >= 0),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (bom_id, operation_id)
);

CREATE TRIGGER bom_operations_updated_at BEFORE UPDATE ON bom_operations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE production_timesheets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    production_order_id UUID NOT NULL REFERENCES production_orders(id) ON DELETE CASCADE,
    operation_id        UUID NOT NULL REFERENCES manufacturing_operations(id),
    user_id             UUID NOT NULL REFERENCES users(id),
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ,
    total_cost          NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER production_timesheets_updated_at BEFORE UPDATE ON production_timesheets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_production_timesheets_order ON production_timesheets(production_order_id);

DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'manufacturing_operations', 'team_labor_rates', 'bom_operations', 'production_timesheets'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid)',
            t
        );
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    manufacturing_operations, team_labor_rates, bom_operations, production_timesheets
    TO app_user;
