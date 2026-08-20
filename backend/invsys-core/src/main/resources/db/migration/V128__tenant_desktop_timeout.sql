-- V128: Tenant-configurable desktop idle soft-lock (NIST 800-63B / SOC 2 CC6)

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS desktop_idle_timeout_minutes INTEGER NOT NULL DEFAULT 30;

ALTER TABLE tenant_settings
    DROP CONSTRAINT IF EXISTS tenant_settings_desktop_idle_timeout_chk;

ALTER TABLE tenant_settings
    ADD CONSTRAINT tenant_settings_desktop_idle_timeout_chk
        CHECK (desktop_idle_timeout_minutes IN (15, 30, 60, 240));

COMMENT ON COLUMN tenant_settings.desktop_idle_timeout_minutes IS
    'Office (non-floor) idle soft-lock timeout in minutes: 15, 30, 60, or 240';
