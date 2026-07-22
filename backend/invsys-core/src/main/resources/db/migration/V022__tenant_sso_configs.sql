-- Track 11: Tenant-scoped SSO / OIDC configuration

CREATE TABLE tenant_sso_configs (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    issuer_url              VARCHAR(255) NOT NULL,
    client_id               VARCHAR(255) NOT NULL,
    encrypted_client_secret BYTEA NOT NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT FALSE,
    force_sso               BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER tenant_sso_configs_updated_at BEFORE UPDATE ON tenant_sso_configs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE tenant_sso_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_sso_configs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_sso_configs;
CREATE POLICY tenant_isolation ON tenant_sso_configs
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_sso_configs TO app_user;
