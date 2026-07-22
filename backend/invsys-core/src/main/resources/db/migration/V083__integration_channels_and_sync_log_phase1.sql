-- Phase 1 Integrations Hub: channel vault rows + richer sync history columns.
-- Extends existing integration_sync_logs (keeps legacy system/entity_id/retry fields).

CREATE TABLE integration_channels (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    channel_type           VARCHAR(30) NOT NULL,
    status                 VARCHAR(30) NOT NULL DEFAULT 'DISCONNECTED',
    encrypted_credentials  BYTEA,
    settings               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_integration_channels_type
        CHECK (channel_type IN ('SHOPIFY', 'AMAZON', 'EDI')),
    CONSTRAINT chk_integration_channels_status
        CHECK (status IN ('ACTIVE', 'DISCONNECTED', 'ERROR')),
    CONSTRAINT uq_integration_channels_tenant_type
        UNIQUE (tenant_id, channel_type)
);

CREATE INDEX idx_integration_channels_tenant_status
    ON integration_channels (tenant_id, status);

CREATE INDEX idx_integration_channels_tenant_type_active
    ON integration_channels (tenant_id, channel_type)
    WHERE status = 'ACTIVE';

CREATE TRIGGER integration_channels_updated_at BEFORE UPDATE ON integration_channels
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE integration_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE integration_channels FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON integration_channels
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration_channels TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON integration_channels TO app_owner;

-- ---------------------------------------------------------------------------
-- Sync log evolution (additive; legacy writers keep working)
-- ---------------------------------------------------------------------------
ALTER TABLE integration_sync_logs
    ADD COLUMN IF NOT EXISTS channel_id UUID REFERENCES integration_channels(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS direction VARCHAR(20),
    ADD COLUMN IF NOT EXISTS external_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS payload_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;

-- New-style rows may key off external_id instead of an internal entity UUID.
ALTER TABLE integration_sync_logs
    ALTER COLUMN entity_id DROP NOT NULL;

-- Backfill error_message from last_error where present.
UPDATE integration_sync_logs
SET error_message = last_error
WHERE error_message IS NULL
  AND last_error IS NOT NULL;

ALTER TABLE integration_sync_logs
    DROP CONSTRAINT IF EXISTS chk_integration_sync_logs_direction;
ALTER TABLE integration_sync_logs
    ADD CONSTRAINT chk_integration_sync_logs_direction
        CHECK (direction IS NULL OR direction IN ('INBOUND', 'OUTBOUND'));

ALTER TABLE integration_sync_logs
    DROP CONSTRAINT IF EXISTS chk_integration_sync_logs_status;
ALTER TABLE integration_sync_logs
    ADD CONSTRAINT chk_integration_sync_logs_status
        CHECK (status IN (
            'PENDING', 'SYNCED', 'SKIPPED',
            'SUCCESS', 'FAILED', 'WARNING'
        ));

CREATE INDEX IF NOT EXISTS idx_sync_logs_tenant_channel_processed
    ON integration_sync_logs (tenant_id, channel_id, processed_at DESC NULLS LAST);

CREATE INDEX IF NOT EXISTS idx_sync_logs_tenant_direction_status
    ON integration_sync_logs (tenant_id, direction, status)
    WHERE direction IS NOT NULL;

COMMENT ON TABLE integration_channels IS
    'Hub connection state + vaulted OAuth/API/EDI credentials per tenant channel';
COMMENT ON COLUMN integration_channels.encrypted_credentials IS
    'AES-GCM blob produced by CredentialVaultService (access tokens, webhook secrets, AS2 keys)';
COMMENT ON COLUMN integration_sync_logs.channel_id IS
    'Optional FK to integration_channels for hub-scoped history';
COMMENT ON COLUMN integration_sync_logs.payload_summary IS
    'Non-secret summary of the synced payload for operator diagnostics';
