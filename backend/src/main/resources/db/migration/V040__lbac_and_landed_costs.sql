-- V040: LBAC confirmation (idempotent) + landed cost allocation audit
-- Note: user_warehouses and allocations.production_order_id already exist from V031 / V013.
-- This migration is additive and safe to re-apply patterns via IF NOT EXISTS.

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS production_order_id UUID NULL REFERENCES production_orders(id);

CREATE TABLE IF NOT EXISTS user_warehouses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, location_id)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'user_warehouses_updated_at'
    ) THEN
        CREATE TRIGGER user_warehouses_updated_at BEFORE UPDATE ON user_warehouses
            FOR EACH ROW EXECUTE FUNCTION set_updated_at();
    END IF;
END $$;

ALTER TABLE user_warehouses ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_warehouses FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON user_warehouses;
CREATE POLICY tenant_isolation ON user_warehouses
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_warehouses TO app_user;
GRANT SELECT ON user_warehouses TO app_owner;

-- Landed cost allocation runs (audit of freight splits; ledger remains append-only)
CREATE TABLE landed_cost_allocations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    supplier_invoice_id  UUID NOT NULL REFERENCES supplier_invoice_ingestions(id) ON DELETE CASCADE,
    purchase_order_id    UUID NOT NULL REFERENCES purchase_orders(id),
    freight_total        NUMERIC(18, 4) NOT NULL CHECK (freight_total >= 0),
    strategy             VARCHAR(20) NOT NULL
        CHECK (strategy IN ('BY_VALUE', 'BY_WEIGHT')),
    line_breakdown       JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_landed_cost_allocations_invoice ON landed_cost_allocations (tenant_id, supplier_invoice_id);
CREATE TRIGGER landed_cost_allocations_updated_at BEFORE UPDATE ON landed_cost_allocations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE landed_cost_allocations ENABLE ROW LEVEL SECURITY;
ALTER TABLE landed_cost_allocations FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON landed_cost_allocations;
CREATE POLICY tenant_isolation ON landed_cost_allocations
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON landed_cost_allocations TO app_user;
GRANT SELECT ON landed_cost_allocations TO app_owner;

COMMENT ON TABLE landed_cost_allocations IS
    'Freight / duty split audit. Quantity-neutral ADJUST ledger rows carry LANDED_COST_ALLOCATION.';
