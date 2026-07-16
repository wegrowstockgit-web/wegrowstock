-- Cross-tenant supply-chain mesh (buyer ↔ seller pairing).
-- Note: V054–V056 already shipped; this is the mesh network migration.

CREATE TABLE tenant_mesh_partners (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,       -- Buying company
    partner_tenant_id  UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,       -- Selling company
    supplier_id        UUID NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,     -- Supplier row in buyer universe
    customer_id        UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,     -- Customer row in seller universe
    connection_status  VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (connection_status IN ('PENDING', 'CONNECTED', 'DISCONNECTED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, partner_tenant_id),
    CHECK (tenant_id <> partner_tenant_id)
);

CREATE INDEX idx_tenant_mesh_partners_buyer_supplier
    ON tenant_mesh_partners (tenant_id, supplier_id)
    WHERE connection_status = 'CONNECTED';

CREATE INDEX idx_tenant_mesh_partners_seller_customer
    ON tenant_mesh_partners (partner_tenant_id, customer_id)
    WHERE connection_status = 'CONNECTED';

DROP TRIGGER IF EXISTS tenant_mesh_partners_updated_at ON tenant_mesh_partners;
CREATE TRIGGER tenant_mesh_partners_updated_at BEFORE UPDATE ON tenant_mesh_partners
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE tenant_mesh_partners ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_mesh_partners FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_mesh_partners;
CREATE POLICY tenant_isolation ON tenant_mesh_partners
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_mesh_partners TO app_user;
GRANT SELECT, INSERT, UPDATE ON tenant_mesh_partners TO app_owner;

-- Cross-tenant FK targets (supplier in buyer / customer in seller) are invisible under
-- FORCE RLS; SECURITY DEFINER + row_security=off performs authoritative pairing writes.
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
        SET supplier_id = EXCLUDED.supplier_id,
            customer_id = EXCLUDED.customer_id,
            connection_status = EXCLUDED.connection_status,
            updated_at = NOW()
    RETURNING id INTO v_id;
    RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION bootstrap_upsert_mesh_partner(
    UUID, UUID, UUID, UUID, VARCHAR) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bootstrap_upsert_mesh_partner(
    UUID, UUID, UUID, UUID, VARCHAR) TO app_user, app_owner;

CREATE POLICY bootstrap_select ON tenant_mesh_partners
    FOR SELECT TO app_owner USING (true);

-- Buyer-side tracking payload for mesh IN_TRANSIT automation
ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS tracking_metadata JSONB NOT NULL DEFAULT '[]'::jsonb;
