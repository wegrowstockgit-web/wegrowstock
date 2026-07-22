-- EasyPost / OAuth vault: track when refresh tokens expire for handshake renewal.
ALTER TABLE integration_credentials
    ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN integration_credentials.refresh_token_expires_at IS
    'UTC instant when vaulted OAuth/refresh material expires; null for static API keys.';
