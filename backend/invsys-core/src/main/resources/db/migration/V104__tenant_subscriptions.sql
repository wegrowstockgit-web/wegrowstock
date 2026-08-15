-- V104: Commercial module entitlements (control-plane managed)
-- Intentionally NOT using standard tenant_isolation write policies — Super Admin
-- updates go through app_owner. app_user may only SELECT their own row (gatekeeper).

CREATE TABLE tenant_subscriptions (
    tenant_id        UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    tier             VARCHAR(50) NOT NULL DEFAULT 'BASIC'
                     CHECK (tier IN ('BASIC', 'INTERMEDIATE', 'ENTERPRISE')),
    enabled_modules  JSONB NOT NULL DEFAULT '["CORE"]'::jsonb,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE tenant_subscriptions IS
    'Commercial module entitlements per tenant. Managed by Control Plane (app_owner); not tenant-user writable.';
COMMENT ON COLUMN tenant_subscriptions.tier IS
    'CommercialTier: BASIC | INTERMEDIATE | ENTERPRISE';
COMMENT ON COLUMN tenant_subscriptions.enabled_modules IS
    'AppModule JSON array, e.g. ["CORE","FINTECH"]';

CREATE TRIGGER tenant_subscriptions_updated_at BEFORE UPDATE ON tenant_subscriptions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Platform Super Admin flag (cross-tenant control plane access)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_super_admin BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.is_super_admin IS
    'When true, JWT includes ROLE_SUPER_ADMIN for Control Plane APIs';

-- Backfill existing tenants with full module set so current deployments keep working.
-- New rows still default to ["CORE"] via column DEFAULT.
INSERT INTO tenant_subscriptions (tenant_id, tier, enabled_modules, updated_at)
SELECT t.id,
       'BASIC',
       '["CORE","B2B_SHOWROOM","FINTECH","MANUFACTURING","SHOPIFY","MRP"]'::jsonb,
       NOW()
FROM tenants t
ON CONFLICT (tenant_id) DO NOTHING;

-- Selective RLS: tenant users read own entitlements; control plane writes via app_owner
ALTER TABLE tenant_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_subscriptions FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_subscription_read ON tenant_subscriptions;
CREATE POLICY tenant_subscription_read ON tenant_subscriptions
    FOR SELECT TO app_user
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

-- Onboarding (same TX as tenant insert) may seed the entitlement row for the active tenant.
DROP POLICY IF EXISTS tenant_subscription_insert ON tenant_subscriptions;
CREATE POLICY tenant_subscription_insert ON tenant_subscriptions
    FOR INSERT TO app_user
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS control_plane_subscription_all ON tenant_subscriptions;
CREATE POLICY control_plane_subscription_all ON tenant_subscriptions
    TO app_owner
    USING (true)
    WITH CHECK (true);

GRANT SELECT, INSERT ON tenant_subscriptions TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_subscriptions TO app_owner;
