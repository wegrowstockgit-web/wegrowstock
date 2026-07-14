-- V043: LBAC confirmation + landed_cost_component on append-only ledger

-- Ensure user_warehouses mapping exists (idempotent; created in V031)
CREATE TABLE IF NOT EXISTS user_warehouses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, location_id)
);

ALTER TABLE user_warehouses ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_warehouses FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON user_warehouses;
CREATE POLICY tenant_isolation ON user_warehouses
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_warehouses TO app_user;
GRANT SELECT ON user_warehouses TO app_owner;

-- Cost component on ledger rows (immutable after insert; qty stays 0 for landed-cost ADJUST)
ALTER TABLE inventory_ledger
    ADD COLUMN IF NOT EXISTS landed_cost_component NUMERIC(19, 4) NOT NULL DEFAULT 0.0000;

COMMENT ON COLUMN inventory_ledger.landed_cost_component IS
    'Allocated landed-cost dollars for this ledger row (LANDED_COST_ALLOCATION ADJUST). Quantity-neutral.';

-- Allow BY_VOLUME strategy label on landed-cost audits
ALTER TABLE landed_cost_allocations DROP CONSTRAINT IF EXISTS landed_cost_allocations_strategy_check;
ALTER TABLE landed_cost_allocations
    ADD CONSTRAINT landed_cost_allocations_strategy_check
    CHECK (strategy IN (
        'BY_VALUE', 'BY_WEIGHT', 'BY_VOLUME',
        'HYBRID', 'VOLUME', 'WEIGHT', 'QUANTITY', 'VALUE', 'CUSTOMS'
    ));
