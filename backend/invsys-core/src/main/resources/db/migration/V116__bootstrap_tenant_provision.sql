-- Control-plane / training sandbox provision new tenant rows as app_owner.
-- tenants has FORCE RLS. V061 only granted SELECT/UPDATE and an UPDATE policy, so
-- INSERT INTO tenants failed in Docker (NOBYPASSRLS). Testcontainers uses a
-- superuser named app_owner, which bypasses RLS and hid the 500.

GRANT INSERT ON tenants TO app_owner;

DROP POLICY IF EXISTS bootstrap_tenant_provision ON tenants;
CREATE POLICY bootstrap_tenant_provision ON tenants
    FOR INSERT TO app_owner
    WITH CHECK (true);

COMMENT ON POLICY bootstrap_tenant_provision ON tenants IS
    'Control plane clone-sandbox and training Flight Simulator insert disposable UAT tenants';
