-- V061: Platform billing subscription_status + fail-closed RLS for SUSPENDED tenants
-- (Prompt referenced V055; that slot is already used by integration refresh tokens.)

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS subscription_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE tenants
    DROP CONSTRAINT IF EXISTS tenants_subscription_status_check;

ALTER TABLE tenants
    ADD CONSTRAINT tenants_subscription_status_check
        CHECK (subscription_status IN ('ACTIVE', 'PAST_DUE', 'SUSPENDED'));

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS stripe_customer_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_tenants_stripe_customer_id
    ON tenants (stripe_customer_id)
    WHERE stripe_customer_id IS NOT NULL;

COMMENT ON COLUMN tenants.subscription_status IS
    'Platform SaaS billing lifecycle: ACTIVE | PAST_DUE | SUSPENDED';
COMMENT ON COLUMN tenants.stripe_customer_id IS
    'Stripe Customer id for platform subscription webhooks (cus_...)';

-- Fail closed: suspended subscriptions are invisible to app_user via RLS.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.relname AS tbl
        FROM pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attname = 'tenant_id' AND NOT a.attisdropped
        WHERE n.nspname = 'public'
          AND c.relkind = 'r'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', r.tbl);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I
             USING (
                 tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid
                 AND EXISTS (
                     SELECT 1 FROM tenants t
                     WHERE t.id = tenant_id
                       AND t.subscription_status IS DISTINCT FROM ''SUSPENDED''
                 )
             )
             WITH CHECK (
                 tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid
                 AND EXISTS (
                     SELECT 1 FROM tenants t
                     WHERE t.id = tenant_id
                       AND t.subscription_status IS DISTINCT FROM ''SUSPENDED''
                 )
             )',
            r.tbl
        );
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I TO app_user', r.tbl);
    END LOOP;
END $$;

DROP POLICY IF EXISTS tenant_isolation ON tenants;
CREATE POLICY tenant_isolation ON tenants
    USING (
        id = nullif(current_setting('app.current_tenant', true), '')::uuid
        AND subscription_status IS DISTINCT FROM 'SUSPENDED'
    )
    WITH CHECK (
        id = nullif(current_setting('app.current_tenant', true), '')::uuid
        AND subscription_status IS DISTINCT FROM 'SUSPENDED'
    );

GRANT SELECT, INSERT, UPDATE, DELETE ON tenants TO app_user;
GRANT SELECT, UPDATE ON tenants TO app_owner;

-- app_owner must update subscription_status from platform webhooks (FORCE RLS).
DROP POLICY IF EXISTS bootstrap_subscription_write ON tenants;
CREATE POLICY bootstrap_subscription_write ON tenants
    FOR UPDATE TO app_owner
    USING (true)
    WITH CHECK (true);
