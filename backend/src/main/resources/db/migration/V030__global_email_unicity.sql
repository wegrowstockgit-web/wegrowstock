-- Global email uniqueness for slugless multi-tenant login resolution.
-- Emails must not collide across tenants so BootstrapJdbc can resolve tenant_id from email alone.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_tenant_id_email_key;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'users_email_key'
    ) THEN
        ALTER TABLE users DROP CONSTRAINT users_email_key;
    END IF;
END $$;

-- Fail fast if seed/data already has cross-tenant email collisions
DO $$
BEGIN
    IF EXISTS (
        SELECT lower(email) FROM users GROUP BY lower(email) HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce global email uniqueness: duplicate emails exist across tenants';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (lower(email));
ALTER TABLE users ADD CONSTRAINT users_email_key UNIQUE (email);
