-- V048: Soft-kitting for e-commerce explode + SUPPLIER portal role/mappings
-- (V047 is allocation device locking)

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS is_soft_kit BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE soft_kit_components (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    parent_kit_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    component_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    quantity     NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT soft_kit_components_parent_ne_component CHECK (parent_kit_id <> component_id),
    UNIQUE (tenant_id, parent_kit_id, component_id)
);

CREATE INDEX idx_soft_kit_components_parent
    ON soft_kit_components (tenant_id, parent_kit_id);

DROP TRIGGER IF EXISTS soft_kit_components_updated_at ON soft_kit_components;
CREATE TRIGGER soft_kit_components_updated_at BEFORE UPDATE ON soft_kit_components
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE soft_kit_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE soft_kit_components FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON soft_kit_components;
CREATE POLICY tenant_isolation ON soft_kit_components
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON soft_kit_components TO app_user;
GRANT SELECT ON soft_kit_components TO app_owner;

-- SUPPLIER role for authenticated vendor ASN portal
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_code_check;
ALTER TABLE roles ADD CONSTRAINT roles_code_check
    CHECK (code IN (
        'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER',
        'B2B_CUSTOMER', 'SUPPLIER'
    ));

CREATE TABLE supplier_user_mappings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    supplier_id UUID NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);

CREATE INDEX idx_supplier_user_mappings_supplier
    ON supplier_user_mappings (tenant_id, supplier_id);

DROP TRIGGER IF EXISTS supplier_user_mappings_updated_at ON supplier_user_mappings;
CREATE TRIGGER supplier_user_mappings_updated_at BEFORE UPDATE ON supplier_user_mappings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE supplier_user_mappings ENABLE ROW LEVEL SECURITY;
ALTER TABLE supplier_user_mappings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON supplier_user_mappings;
CREATE POLICY tenant_isolation ON supplier_user_mappings
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON supplier_user_mappings TO app_user;
GRANT SELECT ON supplier_user_mappings TO app_owner;

ALTER TABLE invitations
    ADD COLUMN IF NOT EXISTS supplier_id UUID NULL REFERENCES suppliers(id);

COMMENT ON TABLE soft_kit_components IS 'E-commerce soft kits: explode parent SKU into pickable component lines on channel order import.';
COMMENT ON TABLE supplier_user_mappings IS 'Maps authenticated SUPPLIER-role users to a vendor for ASN / open PO portal access.';
