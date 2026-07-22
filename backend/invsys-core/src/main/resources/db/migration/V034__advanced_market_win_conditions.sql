-- V034: Advanced market win conditions
-- work centers, quarantine locations, B2B catalogs/volume breaks, magic links, receiving settings, SSO protocol

-- 3) Receiving tolerance defaults (JSONB keys documented; applied at app layer)
-- allow_blind_receiving, over_receipt_tolerance_percent

-- 4) Manufacturing work centers + routing
CREATE TABLE manufacturing_work_centers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    operational_status  VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
        CHECK (operational_status IN ('ACTIVE', 'MAINTENANCE', 'OFFLINE')),
    location_id         UUID REFERENCES locations(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);

CREATE TRIGGER manufacturing_work_centers_updated_at BEFORE UPDATE ON manufacturing_work_centers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE production_orders
    ADD COLUMN IF NOT EXISTS current_work_center_id UUID REFERENCES manufacturing_work_centers(id);

ALTER TABLE production_orders DROP CONSTRAINT IF EXISTS production_orders_status_check;
ALTER TABLE production_orders ADD CONSTRAINT production_orders_status_check
    CHECK (status IN (
        'DRAFT',
        'COMPONENTS_ALLOCATED',
        'IN_ROUTING',
        'WIP',
        'COMPLETED',
        'CANCELLED'
    ));

ALTER TABLE bom_operations
    ADD COLUMN IF NOT EXISTS sequence_order INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS work_center_id UUID REFERENCES manufacturing_work_centers(id),
    ADD COLUMN IF NOT EXISTS depends_on_operation_id UUID REFERENCES bom_operations(id);

CREATE INDEX IF NOT EXISTS idx_bom_operations_bom_seq ON bom_operations(bom_id, sequence_order);

-- 6) B2B catalog restrictions + volume price breaks
CREATE TABLE customer_catalog_restrictions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id  UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    -- polymorphic target: PRODUCT or VARIANT
    target_type  VARCHAR(20) NOT NULL CHECK (target_type IN ('PRODUCT', 'VARIANT')),
    target_id    UUID NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, customer_id, target_type, target_id)
);

CREATE INDEX idx_customer_catalog_restrictions_customer
    ON customer_catalog_restrictions(tenant_id, customer_id);

CREATE TABLE volume_price_breaks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id       UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    min_quantity     NUMERIC(19,4) NOT NULL CHECK (min_quantity > 0),
    discount_percent NUMERIC(5,2) NOT NULL CHECK (discount_percent >= 0 AND discount_percent <= 100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, variant_id, min_quantity)
);

CREATE TRIGGER volume_price_breaks_updated_at BEFORE UPDATE ON volume_price_breaks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_volume_price_breaks_variant ON volume_price_breaks(tenant_id, variant_id);

-- 2) Magic login tokens (single-use, short-lived)
CREATE TABLE magic_login_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(128) NOT NULL UNIQUE,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_magic_login_tokens_user ON magic_login_tokens(user_id) WHERE consumed_at IS NULL;

-- 2) SSO protocol routing (OIDC default; SAML optional)
ALTER TABLE tenant_sso_configs
    ADD COLUMN IF NOT EXISTS protocol VARCHAR(10) NOT NULL DEFAULT 'OIDC'
        CHECK (protocol IN ('OIDC', 'SAML')),
    ADD COLUMN IF NOT EXISTS saml_metadata_url TEXT,
    ADD COLUMN IF NOT EXISTS saml_entity_id VARCHAR(255);

-- 7) Quarantine location type
ALTER TABLE locations DROP CONSTRAINT IF EXISTS locations_type_check;
ALTER TABLE locations ADD CONSTRAINT locations_type_check
    CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN', 'QUARANTINE'));

-- RLS
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'manufacturing_work_centers',
        'customer_catalog_restrictions',
        'volume_price_breaks',
        'magic_login_tokens'
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

-- Bootstrap SELECT for app_owner seed/admin paths
DO $$
BEGIN
    EXECUTE 'CREATE POLICY bootstrap_select ON manufacturing_work_centers FOR SELECT TO app_owner USING (true)';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON manufacturing_work_centers, customer_catalog_restrictions, volume_price_breaks TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON magic_login_tokens TO app_user;
GRANT SELECT ON manufacturing_work_centers, customer_catalog_restrictions, volume_price_breaks, magic_login_tokens TO app_owner;
