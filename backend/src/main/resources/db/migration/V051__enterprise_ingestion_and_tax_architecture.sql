-- V051: Dynamic ingestion mapper, SKU templates, stacking tax schemes
-- (V050 is fulfillment exception shunting)

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS sku_template VARCHAR(255) DEFAULT NULL;

CREATE TABLE tax_schemes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    is_tax_inclusive BOOLEAN NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE tax_scheme_rates (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tax_scheme_id  UUID NOT NULL REFERENCES tax_schemes(id) ON DELETE CASCADE,
    name           VARCHAR(255) NOT NULL,
    rate           NUMERIC(6,4) NOT NULL CHECK (rate >= 0),
    sort_order     INT NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tax_scheme_rates_scheme
    ON tax_scheme_rates (tenant_id, tax_scheme_id, sort_order);

DROP TRIGGER IF EXISTS tax_schemes_updated_at ON tax_schemes;
CREATE TRIGGER tax_schemes_updated_at BEFORE UPDATE ON tax_schemes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DROP TRIGGER IF EXISTS tax_scheme_rates_updated_at ON tax_scheme_rates;
CREATE TRIGGER tax_scheme_rates_updated_at BEFORE UPDATE ON tax_scheme_rates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE tax_schemes ENABLE ROW LEVEL SECURITY;
ALTER TABLE tax_schemes FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tax_schemes;
CREATE POLICY tenant_isolation ON tax_schemes
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE tax_scheme_rates ENABLE ROW LEVEL SECURITY;
ALTER TABLE tax_scheme_rates FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tax_scheme_rates;
CREATE POLICY tenant_isolation ON tax_scheme_rates
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON tax_schemes TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON tax_scheme_rates TO app_user;
GRANT SELECT ON tax_schemes TO app_owner;
GRANT SELECT ON tax_scheme_rates TO app_owner;
