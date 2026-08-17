-- Conditional access: per-role network fence + MFA flag on rotating refresh tokens.
ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS network_access_level VARCHAR(32) NOT NULL DEFAULT 'STRICT_INTERNAL';

ALTER TABLE roles DROP CONSTRAINT IF EXISTS roles_network_access_level_check;
ALTER TABLE roles ADD CONSTRAINT roles_network_access_level_check
    CHECK (network_access_level IN ('STRICT_INTERNAL', 'MFA_OUTSIDE_NETWORK', 'ROAMING'));

UPDATE roles
   SET network_access_level = 'MFA_OUTSIDE_NETWORK'
 WHERE code IN ('OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'VIEWER', 'RETAIL_MANAGER');

UPDATE roles
   SET network_access_level = 'ROAMING'
 WHERE code IN ('B2B_CUSTOMER', 'SUPPLIER');

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS mfa_verified BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN roles.network_access_level IS
    'Conditional access: STRICT_INTERNAL, MFA_OUTSIDE_NETWORK, or ROAMING';
COMMENT ON COLUMN tenant_sso_configs.corporate_cidr_ips IS
    'Internal-network CIDRs (HRD + conditional access allowlist / allowedCidrBlocks)';
