-- V066: Per-user workstation print routing (PDF vs silent ZPL)
-- (Prompt referenced V057; that slot is already used by cross-tenant mesh.)

CREATE TABLE workstation_settings (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    print_mode        VARCHAR(20) NOT NULL DEFAULT 'PDF'
        CHECK (print_mode IN ('PDF', 'ZPL')),
    zpl_printer_name  VARCHAR(100),
    label_format      VARCHAR(20) NOT NULL DEFAULT '4x6',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_workstation_settings_tenant_user
    ON workstation_settings (tenant_id, user_id);

CREATE TRIGGER workstation_settings_updated_at BEFORE UPDATE ON workstation_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE workstation_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE workstation_settings FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON workstation_settings
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON workstation_settings TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON workstation_settings TO app_owner;

ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS label_file_type VARCHAR(20);
