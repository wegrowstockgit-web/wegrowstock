-- Mesh hub: handshake statuses, published catalog listings, PO notes.

ALTER TABLE tenant_mesh_partners
    ALTER COLUMN supplier_id DROP NOT NULL;
ALTER TABLE tenant_mesh_partners
    ALTER COLUMN customer_id DROP NOT NULL;

ALTER TABLE tenant_mesh_partners
    DROP CONSTRAINT IF EXISTS tenant_mesh_partners_connection_status_check;
ALTER TABLE tenant_mesh_partners
    ADD CONSTRAINT tenant_mesh_partners_connection_status_check
        CHECK (connection_status IN ('PENDING', 'REQUESTED', 'CONNECTED', 'DISCONNECTED'));

CREATE OR REPLACE FUNCTION bootstrap_upsert_mesh_partner(
    p_tenant_id UUID,
    p_partner_tenant_id UUID,
    p_supplier_id UUID,
    p_customer_id UUID,
    p_status VARCHAR
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
SET row_security = off
AS $$
DECLARE
    v_id UUID;
BEGIN
    INSERT INTO tenant_mesh_partners (
        tenant_id, partner_tenant_id, supplier_id, customer_id, connection_status)
    VALUES (p_tenant_id, p_partner_tenant_id, p_supplier_id, p_customer_id, p_status)
    ON CONFLICT (tenant_id, partner_tenant_id) DO UPDATE
        SET supplier_id = COALESCE(EXCLUDED.supplier_id, tenant_mesh_partners.supplier_id),
            customer_id = COALESCE(EXCLUDED.customer_id, tenant_mesh_partners.customer_id),
            connection_status = EXCLUDED.connection_status,
            updated_at = NOW()
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$;

CREATE TABLE mesh_catalog_listings (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id            UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    published             BOOLEAN NOT NULL DEFAULT FALSE,
    mesh_wholesale_price  NUMERIC(19, 4),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, variant_id)
);

CREATE INDEX idx_mesh_catalog_listings_published
    ON mesh_catalog_listings (tenant_id, published)
    WHERE published = TRUE;

DROP TRIGGER IF EXISTS mesh_catalog_listings_updated_at ON mesh_catalog_listings;
CREATE TRIGGER mesh_catalog_listings_updated_at BEFORE UPDATE ON mesh_catalog_listings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE mesh_catalog_listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE mesh_catalog_listings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON mesh_catalog_listings;
CREATE POLICY tenant_isolation ON mesh_catalog_listings
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON mesh_catalog_listings TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON mesh_catalog_listings TO app_owner;

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS notes TEXT;
