-- V099: Re-seed role_permissions with per-tenant RLS context.
-- V098's INSERT…SELECT matched 0 rows under FORCE RLS without app.current_tenant.

DO $$
DECLARE
    t UUID;
BEGIN
    FOR t IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.current_tenant', t::text, true);

        INSERT INTO role_permissions (tenant_id, role_id, permission_key, granted)
        SELECT r.tenant_id, r.id, p.permission_key,
               CASE
                   WHEN r.code IN ('OWNER', 'ADMIN') THEN TRUE
                   WHEN r.code = 'WAREHOUSE_MANAGER' THEN p.permission_key IN (
                       'inventory:cost:view',
                       'inventory:adjust',
                       'fulfillment:override',
                       'returns:qc:process',
                       'mrp:run',
                       'printing:thermal',
                       'purchasing:po:approve'
                   )
                   WHEN r.code = 'PICKER' THEN p.permission_key IN (
                       'printing:thermal'
                   )
                   ELSE FALSE
               END
        FROM roles r
        CROSS JOIN (VALUES
            ('inventory:cost:view'),
            ('inventory:adjust'),
            ('purchasing:po:approve'),
            ('sales:invoice:void'),
            ('settings:users:manage'),
            ('fulfillment:override'),
            ('returns:qc:process'),
            ('mrp:run'),
            ('printing:thermal'),
            ('edi:outbound'),
            ('so:discount:override')
        ) AS p(permission_key)
        WHERE r.tenant_id = t
          AND r.code IN ('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER')
        ON CONFLICT (tenant_id, role_id, permission_key) DO UPDATE
            SET granted = EXCLUDED.granted,
                updated_at = NOW();
    END LOOP;

    PERFORM set_config('app.current_tenant', '', true);
END $$;
