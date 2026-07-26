-- V098: Composite granular RBAC permission matrix baseline.
-- Table role_permissions was introduced in V097; this migration seeds defaults
-- and documents the UNION evaluation model (any granted role wins).

-- Ensure table exists for environments that may have drifted (idempotent).
CREATE TABLE IF NOT EXISTS role_permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_key  VARCHAR(100) NOT NULL,
    granted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, role_id, permission_key)
);

ALTER TABLE role_permissions ENABLE ROW LEVEL SECURITY;
ALTER TABLE role_permissions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON role_permissions;
CREATE POLICY tenant_isolation ON role_permissions
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);
GRANT SELECT, INSERT, UPDATE, DELETE ON role_permissions TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON role_permissions TO app_owner;

-- Baseline catalog × role grants for every existing tenant.
-- NOTE: Under FORCE RLS this INSERT…SELECT matches 0 rows unless app.current_tenant
-- is set. V099 re-seeds correctly per tenant via set_config.
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
WHERE r.code IN ('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER')
ON CONFLICT (tenant_id, role_id, permission_key) DO NOTHING;
