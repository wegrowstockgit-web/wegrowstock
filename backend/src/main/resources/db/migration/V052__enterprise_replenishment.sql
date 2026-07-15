-- V052: Zone behavior + algorithmic pick-face replenishment rules
-- (V051 is enterprise ingestion / stacking tax)

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS zone_behavior VARCHAR(50) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE locations DROP CONSTRAINT IF EXISTS locations_zone_behavior_check;
ALTER TABLE locations
    ADD CONSTRAINT locations_zone_behavior_check
        CHECK (zone_behavior IN ('STANDARD', 'PICK_FACE', 'RESERVE', 'RECEIVING'));

COMMENT ON COLUMN locations.zone_behavior IS
    'WMS zone role: PICK_FACE (forward pick), RESERVE (bulk), RECEIVING, or STANDARD.';

CREATE TABLE bin_replenishment_rules (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    location_id  UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    variant_id   UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    min_quantity NUMERIC(19,4) NOT NULL CHECK (min_quantity >= 0),
    max_quantity NUMERIC(19,4) NOT NULL CHECK (max_quantity >= min_quantity),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, location_id, variant_id)
);

CREATE INDEX idx_bin_replenishment_rules_tenant_location
    ON bin_replenishment_rules (tenant_id, location_id);

CREATE INDEX idx_bin_replenishment_rules_tenant_variant
    ON bin_replenishment_rules (tenant_id, variant_id);

DROP TRIGGER IF EXISTS bin_replenishment_rules_updated_at ON bin_replenishment_rules;
CREATE TRIGGER bin_replenishment_rules_updated_at BEFORE UPDATE ON bin_replenishment_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE bin_replenishment_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE bin_replenishment_rules FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON bin_replenishment_rules;
CREATE POLICY tenant_isolation ON bin_replenishment_rules
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON bin_replenishment_rules TO app_user;
GRANT SELECT ON bin_replenishment_rules TO app_owner;

COMMENT ON TABLE bin_replenishment_rules IS
    'Min/max pick-face stock targets used by the algorithmic internal replenishment queue.';
