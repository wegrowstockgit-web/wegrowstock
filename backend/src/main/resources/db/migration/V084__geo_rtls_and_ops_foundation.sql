-- Facility geolocation + RTLS telemetry foundation for enterprise ops.

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS latitude  NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS longitude NUMERIC(10, 7);

COMMENT ON COLUMN locations.latitude IS 'WGS84 latitude for carrier zones / yard geofence';
COMMENT ON COLUMN locations.longitude IS 'WGS84 longitude for carrier zones / yard geofence';

CREATE TABLE rtls_tags (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tag_id          VARCHAR(128) NOT NULL,
    technology      VARCHAR(20) NOT NULL,
    asset_type      VARCHAR(30) NOT NULL,
    asset_ref       UUID,
    label           VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rtls_tags_technology CHECK (technology IN ('BLE_AOA', 'UWB', 'WIFI_RTT', 'OTHER')),
    CONSTRAINT chk_rtls_tags_asset_type CHECK (asset_type IN ('USER', 'PALLET', 'VEHICLE', 'TOTE', 'UNKNOWN')),
    CONSTRAINT uq_rtls_tags_tenant_tag UNIQUE (tenant_id, tag_id)
);

CREATE INDEX idx_rtls_tags_tenant_active ON rtls_tags (tenant_id, active);

CREATE TRIGGER rtls_tags_updated_at BEFORE UPDATE ON rtls_tags
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE rtls_position_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    tag_id          VARCHAR(128) NOT NULL,
    technology      VARCHAR(20) NOT NULL,
    x               NUMERIC(19, 6) NOT NULL,
    y               NUMERIC(19, 6) NOT NULL,
    z               NUMERIC(19, 6),
    accuracy_m      NUMERIC(19, 6),
    heading_deg     NUMERIC(9, 3),
    asset_type      VARCHAR(30),
    asset_ref       UUID,
    warehouse_id    UUID,
    raw_payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
    observed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rtls_positions_tenant_observed
    ON rtls_position_events (tenant_id, observed_at DESC);

CREATE INDEX idx_rtls_positions_tenant_tag
    ON rtls_position_events (tenant_id, tag_id, observed_at DESC);

CREATE TRIGGER rtls_position_events_updated_at BEFORE UPDATE ON rtls_position_events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE rtls_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE rtls_tags FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rtls_tags
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE rtls_position_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE rtls_position_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rtls_position_events
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON rtls_tags, rtls_position_events TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON rtls_tags, rtls_position_events TO app_owner;
