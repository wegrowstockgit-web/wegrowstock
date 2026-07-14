-- Allow BootstrapJdbc (app_owner) to resolve LBAC warehouse mappings pre/post auth
-- without a tenant GUC — same pattern as V010/V011 for users and tenants.

DROP POLICY IF EXISTS bootstrap_owner_select ON user_warehouses;
CREATE POLICY bootstrap_owner_select ON user_warehouses
    FOR SELECT TO app_owner
    USING (true);

DROP POLICY IF EXISTS bootstrap_owner_select_locations ON locations;
CREATE POLICY bootstrap_owner_select_locations ON locations
    FOR SELECT TO app_owner
    USING (true);

GRANT SELECT ON user_warehouses TO app_owner;
GRANT SELECT ON locations TO app_owner;
