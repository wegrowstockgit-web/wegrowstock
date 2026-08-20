-- Control-plane governance: feature flags, tenant throttle, impersonation actor type.

CREATE TABLE feature_flags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key    VARCHAR(64) NOT NULL UNIQUE,
    description TEXT,
    is_global   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tenant_feature_flags (
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    flag_id     UUID NOT NULL REFERENCES feature_flags(id) ON DELETE CASCADE,
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (tenant_id, flag_id)
);

CREATE INDEX idx_tenant_feature_flags_flag ON tenant_feature_flags (flag_id);

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS custom_rate_limit INT DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS is_throttled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tenants DROP CONSTRAINT IF EXISTS tenants_status_check;
ALTER TABLE tenants ADD CONSTRAINT tenants_status_check
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED', 'PURGED'));

ALTER TABLE platform_audit_logs
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(64) NOT NULL DEFAULT 'PLATFORM_ADMIN';

CREATE INDEX IF NOT EXISTS idx_platform_audit_logs_actor_type
    ON platform_audit_logs (actor_type, created_at DESC);

UPDATE platform_audit_logs
   SET actor_type = 'PLATFORM_ADMIN_IMPERSONATION'
 WHERE action = 'TENANT_IMPERSONATE'
   AND actor_type = 'PLATFORM_ADMIN';

COMMENT ON TABLE feature_flags IS
    'Platform progressive-delivery flags; global release or per-tenant beta overrides.';
COMMENT ON COLUMN tenants.custom_rate_limit IS
    'Optional per-tenant API token-bucket capacity (requests per second). NULL uses the tier default.';
COMMENT ON COLUMN tenants.is_throttled IS
    'Emergency kill switch: when true, authenticated tenant traffic is rejected with HTTP 429.';
COMMENT ON COLUMN platform_audit_logs.actor_type IS
    'SOC 2 actor classification (PLATFORM_ADMIN or PLATFORM_ADMIN_IMPERSONATION).';

GRANT SELECT ON feature_flags TO app_user;
GRANT SELECT ON tenant_feature_flags TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON feature_flags TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_feature_flags TO app_owner;
