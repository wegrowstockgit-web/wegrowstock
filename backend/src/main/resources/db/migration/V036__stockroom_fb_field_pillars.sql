-- V036: Stockroom / F&B genealogy / Field service van-stock pillars

-- 1) Cost centers & internal requisitions
CREATE TABLE cost_centers (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code         VARCHAR(50) NOT NULL,
    name         VARCHAR(100) NOT NULL,
    budget       NUMERIC(19,4),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, code)
);

CREATE TRIGGER cost_centers_updated_at BEFORE UPDATE ON cost_centers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE internal_requisitions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    requisition_number   VARCHAR(50) NOT NULL,
    cost_center_id       UUID NOT NULL REFERENCES cost_centers(id),
    requested_by_user_id UUID REFERENCES users(id),
    status               VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'ISSUED', 'CANCELLED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, requisition_number)
);

CREATE TRIGGER internal_requisitions_updated_at BEFORE UPDATE ON internal_requisitions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE internal_requisition_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    requisition_id  UUID NOT NULL REFERENCES internal_requisitions(id) ON DELETE CASCADE,
    variant_id      UUID NOT NULL REFERENCES product_variants(id),
    qty_requested   NUMERIC(19,4) NOT NULL CHECK (qty_requested > 0),
    qty_issued      NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (qty_issued >= 0),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER internal_requisition_lines_updated_at BEFORE UPDATE ON internal_requisition_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_internal_requisition_lines_req ON internal_requisition_lines(requisition_id);

-- 3) Vehicle locations + assignments
ALTER TABLE locations DROP CONSTRAINT IF EXISTS locations_type_check;
ALTER TABLE locations ADD CONSTRAINT locations_type_check
    CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN', 'QUARANTINE', 'VEHICLE'));

CREATE TABLE vehicle_assignments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    location_id         UUID NOT NULL REFERENCES locations(id),
    technician_user_id  UUID NOT NULL REFERENCES users(id),
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    returned_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER vehicle_assignments_updated_at BEFORE UPDATE ON vehicle_assignments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- One active vehicle per technician (returned_at IS NULL)
CREATE UNIQUE INDEX ux_vehicle_assignments_active_tech
    ON vehicle_assignments (tenant_id, technician_user_id)
    WHERE returned_at IS NULL;

CREATE OR REPLACE FUNCTION enforce_vehicle_assignment_location()
RETURNS TRIGGER AS $$
DECLARE
    loc_type TEXT;
    loc_tenant UUID;
BEGIN
    SELECT type, tenant_id INTO loc_type, loc_tenant
    FROM locations WHERE id = NEW.location_id;
    IF loc_type IS NULL THEN
        RAISE EXCEPTION 'vehicle_assignments.location_id not found';
    END IF;
    IF loc_type <> 'VEHICLE' THEN
        RAISE EXCEPTION 'vehicle_assignments.location_id must reference a VEHICLE location (got %)', loc_type;
    END IF;
    IF loc_tenant <> NEW.tenant_id THEN
        RAISE EXCEPTION 'vehicle_assignments tenant/location mismatch';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER vehicle_assignments_location_type
    BEFORE INSERT OR UPDATE OF location_id ON vehicle_assignments
    FOR EACH ROW EXECUTE FUNCTION enforce_vehicle_assignment_location();

-- RLS
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'cost_centers',
        'internal_requisitions',
        'internal_requisition_lines',
        'vehicle_assignments'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid)',
            t
        );
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON cost_centers, internal_requisitions, internal_requisition_lines, vehicle_assignments TO app_user;
GRANT SELECT ON cost_centers, internal_requisitions, internal_requisition_lines, vehicle_assignments TO app_owner;
