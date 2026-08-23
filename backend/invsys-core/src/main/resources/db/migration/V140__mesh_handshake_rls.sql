-- Docker app_owner has NOBYPASSRLS. SET row_security = off on a SECURITY DEFINER
-- function then fails with "query would be affected by row-level security policy"
-- because FORCE RLS still applies to the table owner. Use a permissive owner
-- policy instead so handshake writes succeed on the live stack and in tests.

DROP POLICY IF EXISTS bootstrap_write_mesh_partners ON tenant_mesh_partners;
CREATE POLICY bootstrap_write_mesh_partners ON tenant_mesh_partners
    FOR ALL TO app_owner
    USING (true)
    WITH CHECK (true);

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

REVOKE ALL ON FUNCTION bootstrap_upsert_mesh_partner(
    UUID, UUID, UUID, UUID, VARCHAR) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bootstrap_upsert_mesh_partner(
    UUID, UUID, UUID, UUID, VARCHAR) TO app_user, app_owner;
