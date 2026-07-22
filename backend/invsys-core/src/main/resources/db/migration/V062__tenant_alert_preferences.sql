-- V062: Tenant alert preferences for integration failure notifications
-- (Prompt referenced V056; that slot is already used by domain DNS verification.)

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS alert_email VARCHAR(255) NULL;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS slack_webhook_url VARCHAR(1024) NULL;

COMMENT ON COLUMN tenant_settings.alert_email IS
    'IT contact email for integration failure alerts';
COMMENT ON COLUMN tenant_settings.slack_webhook_url IS
    'Incoming Slack webhook URL for integration failure alerts';

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_settings TO app_user;
