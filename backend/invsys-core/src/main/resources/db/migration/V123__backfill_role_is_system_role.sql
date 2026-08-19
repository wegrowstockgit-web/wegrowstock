-- V122's blanket UPDATE was a no-op under tenant RLS (no app.current_tenant).
-- Backfill platform baseline roles per tenant so custom-role locks stick.
DO $$
DECLARE
    t UUID;
BEGIN
    FOR t IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.current_tenant', t::text, true);
        UPDATE roles
           SET is_system_role = TRUE
         WHERE tenant_id = t
           AND code IN (
               'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER',
               'RETAIL_CASHIER', 'RETAIL_MANAGER', 'B2B_CUSTOMER', 'SUPPLIER'
           );
    END LOOP;
    PERFORM set_config('app.current_tenant', '', true);
END $$;
