-- V118: Isolated Retail POS roles. Cashiers operate the register; store managers supervise voids.
-- V117 is already enterprise B2B/mesh entitlements — this must be V118+.

ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_code_check;
ALTER TABLE roles ADD CONSTRAINT roles_code_check
    CHECK (code IN (
        'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER',
        'B2B_CUSTOMER', 'SUPPLIER', 'RETAIL_CASHIER', 'RETAIL_MANAGER'
    ));

DO $$
DECLARE
    t UUID;
BEGIN
    FOR t IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.current_tenant', t::text, true);

        INSERT INTO roles (tenant_id, code)
        SELECT t, code
        FROM (VALUES ('RETAIL_CASHIER'), ('RETAIL_MANAGER')) AS r(code)
        WHERE NOT EXISTS (
            SELECT 1 FROM roles existing
            WHERE existing.tenant_id = t AND existing.code = r.code
        );

        INSERT INTO role_permissions (tenant_id, role_id, permission_key, granted)
        SELECT r.tenant_id, r.id, p.permission_key,
               CASE
                   WHEN r.code IN ('OWNER', 'ADMIN') THEN TRUE
                   WHEN r.code IN ('WAREHOUSE_MANAGER', 'RETAIL_MANAGER') THEN TRUE
                   WHEN r.code = 'RETAIL_CASHIER' THEN p.permission_key = 'pos.operate'
                   ELSE FALSE
               END
        FROM roles r
        CROSS JOIN (VALUES
            ('pos.operate'),
            ('pos.supervise')
        ) AS p(permission_key)
        WHERE r.tenant_id = t
          AND r.code IN (
              'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER',
              'RETAIL_CASHIER', 'RETAIL_MANAGER'
          )
        ON CONFLICT (tenant_id, role_id, permission_key) DO UPDATE
            SET granted = EXCLUDED.granted,
                updated_at = NOW();
    END LOOP;

    PERFORM set_config('app.current_tenant', '', true);
END $$;
