-- Location-Based Access Control: map users to authorized warehouse locations.

CREATE TABLE user_warehouses (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, location_id)
);

CREATE TRIGGER user_warehouses_updated_at BEFORE UPDATE ON user_warehouses
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_user_warehouses_tenant_user ON user_warehouses(tenant_id, user_id);
CREATE INDEX idx_user_warehouses_location ON user_warehouses(location_id);

-- Target location must be a WAREHOUSE (same tenant).
CREATE OR REPLACE FUNCTION enforce_user_warehouse_is_warehouse()
RETURNS TRIGGER AS $$
DECLARE
    loc_type TEXT;
    loc_tenant UUID;
BEGIN
    SELECT type, tenant_id INTO loc_type, loc_tenant
    FROM locations WHERE id = NEW.location_id;

    IF loc_type IS NULL THEN
        RAISE EXCEPTION 'user_warehouses.location_id % does not exist', NEW.location_id;
    END IF;
    IF loc_tenant IS DISTINCT FROM NEW.tenant_id THEN
        RAISE EXCEPTION 'user_warehouses location tenant mismatch';
    END IF;
    IF loc_type <> 'WAREHOUSE' THEN
        RAISE EXCEPTION 'user_warehouses.location_id must reference a WAREHOUSE location (got %)', loc_type;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_warehouses_is_warehouse
    BEFORE INSERT OR UPDATE ON user_warehouses
    FOR EACH ROW EXECUTE FUNCTION enforce_user_warehouse_is_warehouse();

ALTER TABLE user_warehouses ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_warehouses FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON user_warehouses;
CREATE POLICY tenant_isolation ON user_warehouses
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_warehouses TO app_user;
