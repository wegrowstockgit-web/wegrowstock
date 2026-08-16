-- Self-serve wholesale applications + customers:manage permission.

CREATE TABLE wholesale_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    company_name    VARCHAR(255) NOT NULL,
    tax_id          VARCHAR(64) NOT NULL,
    contact_name    VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    phone           VARCHAR(64),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    customer_id     UUID REFERENCES customers(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT wholesale_applications_status_check
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_wholesale_applications_tenant_status
    ON wholesale_applications (tenant_id, status, created_at DESC);

CREATE UNIQUE INDEX uq_wholesale_applications_pending_email
    ON wholesale_applications (tenant_id, lower(email))
    WHERE status = 'PENDING';

ALTER TABLE wholesale_applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE wholesale_applications FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON wholesale_applications
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON wholesale_applications TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON wholesale_applications TO app_owner;

-- Seed customers:manage for existing tenants (FORCE RLS requires per-tenant GUC).
DO $$
DECLARE
    t UUID;
BEGIN
    FOR t IN SELECT id FROM tenants LOOP
        PERFORM set_config('app.current_tenant', t::text, true);

        INSERT INTO role_permissions (tenant_id, role_id, permission_key, granted)
        SELECT r.tenant_id, r.id, 'customers:manage',
               CASE
                   WHEN r.code IN ('OWNER', 'ADMIN') THEN TRUE
                   ELSE FALSE
               END
        FROM roles r
        WHERE r.tenant_id = t
          AND r.code IN ('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER')
        ON CONFLICT (tenant_id, role_id, permission_key) DO NOTHING;
    END LOOP;

    PERFORM set_config('app.current_tenant', '', true);
END $$;
