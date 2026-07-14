-- V042: Hybrid waterfall landed costs — product categories + variant dimensional hooks
-- (V041 already reserved for quantity-neutral LANDED_COST_ALLOCATION ledger rows)

CREATE TABLE product_categories (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    median_weight  NUMERIC(19, 4) NULL,
    median_volume  NUMERIC(19, 4) NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

CREATE INDEX idx_product_categories_tenant ON product_categories (tenant_id);
CREATE TRIGGER product_categories_updated_at BEFORE UPDATE ON product_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE product_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_categories FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON product_categories;
CREATE POLICY tenant_isolation ON product_categories
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON product_categories TO app_user;
GRANT SELECT ON product_categories TO app_owner;

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS category_id UUID NULL REFERENCES product_categories(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS volume NUMERIC(19, 4) NULL;

CREATE INDEX IF NOT EXISTS idx_product_variants_category ON product_variants (tenant_id, category_id)
    WHERE category_id IS NOT NULL;

-- Expand landed-cost strategy vocabulary for hybrid waterfall
ALTER TABLE landed_cost_allocations DROP CONSTRAINT IF EXISTS landed_cost_allocations_strategy_check;
ALTER TABLE landed_cost_allocations
    ADD CONSTRAINT landed_cost_allocations_strategy_check
    CHECK (strategy IN (
        'BY_VALUE', 'BY_WEIGHT',
        'HYBRID', 'VOLUME', 'WEIGHT', 'QUANTITY', 'VALUE', 'CUSTOMS'
    ));

COMMENT ON TABLE product_categories IS
    'Category medians for hybrid landed-cost cascade when variant weight/volume is NULL.';
