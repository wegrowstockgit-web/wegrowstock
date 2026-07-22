-- V038: Hardware terminal context gate (SSID + geofence → warehouse)
-- Beats mid-market WMS passive warehouse pickers (Cin7 Working Area / inFlow location select)

CREATE TABLE warehouse_context_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    location_id     UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    match_type      VARCHAR(20) NOT NULL
        CHECK (match_type IN ('WIFI_SSID', 'GEOFENCE')),
    -- WIFI_SSID: exact/case-insensitive SSID of handheld AP / site WLAN
    ssid            VARCHAR(255),
    -- GEOFENCE: circular fence around facility (field vans / outdoor yards)
    latitude        NUMERIC(10, 7),
    longitude       NUMERIC(10, 7),
    radius_meters   NUMERIC(10, 2),
    priority        INT NOT NULL DEFAULT 100,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    label           VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT warehouse_context_rules_wifi_chk CHECK (
        match_type <> 'WIFI_SSID' OR (ssid IS NOT NULL AND length(trim(ssid)) > 0)
    ),
    CONSTRAINT warehouse_context_rules_geo_chk CHECK (
        match_type <> 'GEOFENCE'
        OR (latitude IS NOT NULL AND longitude IS NOT NULL AND radius_meters IS NOT NULL AND radius_meters > 0)
    )
);

CREATE INDEX idx_warehouse_context_rules_tenant ON warehouse_context_rules (tenant_id, enabled, priority);
CREATE INDEX idx_warehouse_context_rules_ssid ON warehouse_context_rules (tenant_id, lower(ssid))
    WHERE match_type = 'WIFI_SSID' AND enabled;

CREATE TRIGGER warehouse_context_rules_updated_at BEFORE UPDATE ON warehouse_context_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE warehouse_context_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE warehouse_context_rules FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON warehouse_context_rules;
CREATE POLICY tenant_isolation ON warehouse_context_rules
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

DO $$
BEGIN
    EXECUTE 'CREATE POLICY bootstrap_select ON warehouse_context_rules FOR SELECT TO app_owner USING (true)';
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON warehouse_context_rules TO app_user;
GRANT SELECT ON warehouse_context_rules TO app_owner;

COMMENT ON TABLE warehouse_context_rules IS
    'Maps Wi-Fi SSIDs and GPS geofences to warehouse locations for automated terminal context gating';
