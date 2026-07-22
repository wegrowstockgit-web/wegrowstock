-- V065: 3D cartonization masters + shipment pack metadata
-- product_variants.length/width/height already exist (V024); ensure present for older envs.

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS length NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS width NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS height NUMERIC(10,4);

CREATE TABLE shipping_cartons (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name            VARCHAR(120) NOT NULL,
    length          NUMERIC(10,4) NOT NULL CHECK (length > 0),
    width           NUMERIC(10,4) NOT NULL CHECK (width > 0),
    height          NUMERIC(10,4) NOT NULL CHECK (height > 0),
    max_weight      NUMERIC(10,4) NOT NULL CHECK (max_weight > 0),
    empty_weight    NUMERIC(10,4) NOT NULL DEFAULT 0 CHECK (empty_weight >= 0),
    dim_unit        VARCHAR(10) NOT NULL DEFAULT 'in',
    weight_unit     VARCHAR(10) NOT NULL DEFAULT 'lb',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

CREATE INDEX idx_shipping_cartons_tenant_active ON shipping_cartons (tenant_id, active);

CREATE TRIGGER shipping_cartons_updated_at BEFORE UPDATE ON shipping_cartons
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE shipping_cartons ENABLE ROW LEVEL SECURITY;
ALTER TABLE shipping_cartons FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON shipping_cartons
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON shipping_cartons TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON shipping_cartons TO app_owner;

ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS carton_id UUID REFERENCES shipping_cartons(id),
    ADD COLUMN IF NOT EXISTS carton_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS length NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS width NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS height NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS volumetric_weight NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS service_level VARCHAR(80);

ALTER TABLE shipments DROP CONSTRAINT IF EXISTS shipments_status_check;
ALTER TABLE shipments
    ADD CONSTRAINT shipments_status_check
    CHECK (status IN ('PENDING', 'LABEL_CREATED', 'SHIPPED', 'DELIVERED', 'CANCELLED'));
