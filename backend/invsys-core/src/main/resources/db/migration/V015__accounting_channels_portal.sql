CREATE TABLE account_mappings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    system              VARCHAR(50) NOT NULL,
    account_type        VARCHAR(100) NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, system, account_type)
);

CREATE TRIGGER account_mappings_updated_at BEFORE UPDATE ON account_mappings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE channel_integrations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    platform          VARCHAR(50) NOT NULL,
    shop_identifier   VARCHAR(255) NOT NULL,
    credential_id     UUID,
    status            VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (platform, shop_identifier)
);

CREATE TRIGGER channel_integrations_updated_at BEFORE UPDATE ON channel_integrations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE customer_user_mappings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);

CREATE TRIGGER customer_user_mappings_updated_at BEFORE UPDATE ON customer_user_mappings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE customer_price_tiers (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name             VARCHAR(100) NOT NULL,
    discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0.00
        CHECK (discount_percent >= 0 AND discount_percent <= 100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER customer_price_tiers_updated_at BEFORE UPDATE ON customer_price_tiers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE customers ADD COLUMN price_tier_id UUID NULL REFERENCES customer_price_tiers(id);
ALTER TABLE invitations ADD COLUMN customer_id UUID NULL REFERENCES customers(id);

-- Extend roles CHECK for B2B portal (M7)
ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_code_check;
ALTER TABLE roles ADD CONSTRAINT roles_code_check
    CHECK (code IN ('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER', 'B2B_CUSTOMER'));

-- RLS on new tenant-scoped tables
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'account_mappings', 'channel_integrations',
        'customer_user_mappings', 'customer_price_tiers'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true)::uuid)',
            t
        );
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    account_mappings, integration_sync_logs, channel_integrations,
    customer_user_mappings, customer_price_tiers
TO app_user;
