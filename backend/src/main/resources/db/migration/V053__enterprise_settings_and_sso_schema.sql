-- V053: SSO schema hardening, OAuth callback state, cross-dock allocation status
-- (V052 is pick-face replenishment; tenant_sso_configs already created in V022/V034)

-- Widen issuer / client columns for enterprise IdP URLs (Okta, Azure AD, etc.)
ALTER TABLE tenant_sso_configs
    ALTER COLUMN issuer_url TYPE VARCHAR(1024),
    ALTER COLUMN client_id TYPE VARCHAR(512);

ALTER TABLE tenant_sso_configs
    ALTER COLUMN saml_metadata_url TYPE VARCHAR(1024);

-- Compatibility aliases used by plug-and-play docs (issuer_url remains canonical)
COMMENT ON COLUMN tenant_sso_configs.issuer_url IS 'IdP issuer / OIDC discovery base (idp_issuer)';
COMMENT ON COLUMN tenant_sso_configs.encrypted_client_secret IS 'AES-GCM vault ciphertext for OAuth client_secret';
COMMENT ON COLUMN tenant_sso_configs.enabled IS 'is_active — when true, IdP registration is live';

-- Ensure RLS remains forced with null-safe tenant GUC (idempotent)
ALTER TABLE tenant_sso_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_sso_configs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_sso_configs;
CREATE POLICY tenant_isolation ON tenant_sso_configs
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_sso_configs TO app_user;
GRANT SELECT ON tenant_sso_configs TO app_owner;

-- Ephemeral OAuth state for public multi-tenant redirect listener (pre-tenant-context)
CREATE TABLE IF NOT EXISTS oauth_callback_states (
    state       VARCHAR(128) PRIMARY KEY,
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    provider    VARCHAR(64) NOT NULL,
    payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_oauth_callback_states_tenant
    ON oauth_callback_states (tenant_id, expires_at);

-- No RLS: state tokens are consumed before TenantContext is established (bootstrap path).
GRANT SELECT, INSERT, UPDATE, DELETE ON oauth_callback_states TO app_user;
GRANT SELECT ON oauth_callback_states TO app_owner;

-- Cross-dock routing status on allocations (not ledger — status transition only)
ALTER TABLE allocations DROP CONSTRAINT IF EXISTS allocations_status_check;
ALTER TABLE allocations
    ADD CONSTRAINT allocations_status_check
        CHECK (status IN (
            'ACTIVE',
            'RELEASED',
            'CONSUMED',
            'CANCELLED',
            'EXCEPTION_DAMAGED_BARCODE',
            'CROSS_DOCK_ROUTED'
        ));
