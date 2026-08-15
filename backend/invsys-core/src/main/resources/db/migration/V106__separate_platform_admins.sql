-- Platform control-plane admins live outside tenant RLS (users table).
-- Prevents Super Admin identity from sharing tenant-scoped row policies.

CREATE TABLE platform_admins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT platform_admins_email_uq UNIQUE (email)
);

COMMENT ON TABLE platform_admins IS
    'Control-plane Super Admin identities. Not tenant-scoped; never subject to users RLS.';

CREATE TABLE platform_admin_refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id        UUID NOT NULL REFERENCES platform_admins(id) ON DELETE CASCADE,
    token_hash      VARCHAR(128) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT platform_admin_refresh_tokens_hash_uq UNIQUE (token_hash)
);

CREATE INDEX idx_platform_admin_refresh_tokens_admin
    ON platform_admin_refresh_tokens (admin_id);

-- Migrate any legacy users flagged as platform super-admins.
INSERT INTO platform_admins (id, email, password_hash, active)
SELECT u.id, u.email, u.password_hash, (u.status = 'ACTIVE')
FROM users u
WHERE u.is_super_admin = TRUE
ON CONFLICT (email) DO NOTHING;

ALTER TABLE users DROP COLUMN IF EXISTS is_super_admin;

-- Control-plane API (and WMS share app_user). Table is not RLS-scoped; do not expose via WMS HTTP APIs.
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_admins TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_admin_refresh_tokens TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_admins TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_admin_refresh_tokens TO app_owner;
