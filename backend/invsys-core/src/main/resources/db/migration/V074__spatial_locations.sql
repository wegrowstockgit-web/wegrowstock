-- V074: Spatial Digital Twin — X/Y/Z coordinates + walkable graph for A* routing
-- (Prompt referenced V062; that version is already used by tenant alert preferences.)

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS coord_x NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS coord_y NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS coord_z NUMERIC(19, 4);

COMMENT ON COLUMN locations.coord_x IS 'Digital Twin floor X coordinate (warehouse plane)';
COMMENT ON COLUMN locations.coord_y IS 'Digital Twin floor Y coordinate (warehouse plane)';
COMMENT ON COLUMN locations.coord_z IS 'Optional vertical / mezzanine elevation';

CREATE TABLE walkable_edges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    node_a_id   UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    node_b_id   UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    distance    NUMERIC(19, 4) NOT NULL CHECK (distance > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT walkable_edges_nodes_distinct CHECK (node_a_id <> node_b_id),
    CONSTRAINT walkable_edges_unique_pair UNIQUE (tenant_id, node_a_id, node_b_id)
);

CREATE INDEX idx_walkable_edges_tenant_a ON walkable_edges (tenant_id, node_a_id);
CREATE INDEX idx_walkable_edges_tenant_b ON walkable_edges (tenant_id, node_b_id);

CREATE TRIGGER walkable_edges_updated_at BEFORE UPDATE ON walkable_edges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE walkable_edges ENABLE ROW LEVEL SECURITY;
ALTER TABLE walkable_edges FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON walkable_edges
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON walkable_edges TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON walkable_edges TO app_owner;

-- Seed Euclidean-ish coords for leaf BINs that lack them (deterministic from sequence_index + path).
UPDATE locations loc
SET coord_x = (COALESCE(loc.sequence_index, 0) % 20) * 10
            + (ABS(HASHTEXT(loc.code)) % 10),
    coord_y = (COALESCE(loc.sequence_index, 0) / 20) * 10
            + (ABS(HASHTEXT(loc.path)) % 10)
WHERE loc.type = 'BIN'
  AND loc.coord_x IS NULL
  AND loc.coord_y IS NULL;
