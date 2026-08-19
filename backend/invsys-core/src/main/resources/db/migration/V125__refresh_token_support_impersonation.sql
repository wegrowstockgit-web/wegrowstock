-- Persist support-impersonation on rotating refresh tokens so a fenced tenant
-- remains reachable for the full session after platform-admin handoff.
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS support_impersonation BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN refresh_tokens.support_impersonation IS
    'True when this session was minted by control-plane owner impersonation (skips warehouse network fence)';
