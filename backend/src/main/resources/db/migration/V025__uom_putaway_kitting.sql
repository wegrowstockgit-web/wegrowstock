-- Track 18-20: UoM conversions, directed putaway, dynamic kitting

ALTER TABLE products DROP COLUMN IF EXISTS uom;

CREATE TABLE variant_uom_conversions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id        UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    uom_type          VARCHAR(20) NOT NULL CHECK (uom_type IN ('PURCHASING', 'STANDARD', 'SALES')),
    unit_name         VARCHAR(50) NOT NULL,
    conversion_ratio  NUMERIC(19,4) NOT NULL CHECK (conversion_ratio > 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, variant_id, uom_type)
);

CREATE TRIGGER variant_uom_conversions_updated_at BEFORE UPDATE ON variant_uom_conversions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS default_location_id UUID REFERENCES locations(id),
    ADD COLUMN IF NOT EXISTS dims JSONB NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS is_kit BOOLEAN NOT NULL DEFAULT FALSE;

-- variant_uom_conversions RLS
ALTER TABLE variant_uom_conversions ENABLE ROW LEVEL SECURITY;
ALTER TABLE variant_uom_conversions FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON variant_uom_conversions;
CREATE POLICY tenant_isolation ON variant_uom_conversions
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON variant_uom_conversions TO app_user;
