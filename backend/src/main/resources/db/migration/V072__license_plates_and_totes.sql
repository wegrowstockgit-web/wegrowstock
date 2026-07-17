-- V072: License plates (LPNs), tote identifiers on pick tasks, ledger/level lpn_id

CREATE TABLE license_plates (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    lpn_barcode  VARCHAR(50) NOT NULL,
    location_id  UUID REFERENCES locations(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'IN_TRANSIT', 'CLOSED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, lpn_barcode)
);

CREATE INDEX idx_license_plates_tenant_location
    ON license_plates (tenant_id, location_id);

CREATE TRIGGER license_plates_updated_at BEFORE UPDATE ON license_plates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE license_plates ENABLE ROW LEVEL SECURITY;
ALTER TABLE license_plates FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON license_plates
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON license_plates TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON license_plates TO app_owner;

ALTER TABLE inventory_levels
    ADD COLUMN IF NOT EXISTS lpn_id UUID REFERENCES license_plates(id);

ALTER TABLE inventory_ledger
    ADD COLUMN IF NOT EXISTS lpn_id UUID;

CREATE INDEX IF NOT EXISTS idx_inventory_levels_lpn
    ON inventory_levels (tenant_id, lpn_id)
    WHERE lpn_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inventory_ledger_lpn
    ON inventory_ledger (tenant_id, lpn_id)
    WHERE lpn_id IS NOT NULL;

-- Demo/seed DBs may already have duplicate floor levels for the same
-- (tenant, variant, location, lot). Merge them before the LPN-aware unique key
-- (NULLS NOT DISTINCT treats multiple NULL lpn_id rows as collisions).
-- Flyway runs as app_owner without BYPASSRLS; FORCE RLS would hide all rows
-- when app.current_tenant is unset, so temporarily disable RLS for the merge.
ALTER TABLE inventory_levels DISABLE ROW LEVEL SECURITY;

CREATE TEMP TABLE inv_level_merge AS
SELECT
    (ARRAY_AGG(id ORDER BY id::text))[1] AS keep_id,
    tenant_id,
    variant_id,
    location_id,
    lot_id,
    lpn_id,
    SUM(on_hand) AS on_hand,
    SUM(allocated) AS allocated,
    (ARRAY_AGG(owner_customer_id) FILTER (WHERE owner_customer_id IS NOT NULL))[1] AS owner_customer_id
FROM inventory_levels
GROUP BY tenant_id, variant_id, location_id, lot_id, lpn_id
HAVING COUNT(*) > 1;

UPDATE inventory_levels il
SET on_hand = m.on_hand,
    allocated = m.allocated,
    owner_customer_id = COALESCE(m.owner_customer_id, il.owner_customer_id),
    updated_at = NOW()
FROM inv_level_merge m
WHERE il.id = m.keep_id;

DELETE FROM inventory_levels il
USING inv_level_merge m
WHERE il.tenant_id = m.tenant_id
  AND il.variant_id = m.variant_id
  AND il.location_id = m.location_id
  AND il.lot_id IS NOT DISTINCT FROM m.lot_id
  AND il.lpn_id IS NOT DISTINCT FROM m.lpn_id
  AND il.id <> m.keep_id;

DROP TABLE inv_level_merge;

ALTER TABLE inventory_levels ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_levels FORCE ROW LEVEL SECURITY;

-- Expand level uniqueness so LPN-scoped stock does not collide with floor stock.
ALTER TABLE inventory_levels
    DROP CONSTRAINT IF EXISTS inventory_levels_tenant_id_variant_id_location_id_lot_id_key;

ALTER TABLE inventory_levels
    ADD CONSTRAINT inventory_levels_tenant_variant_loc_lot_lpn_key
        UNIQUE NULLS NOT DISTINCT (tenant_id, variant_id, location_id, lot_id, lpn_id);

CREATE OR REPLACE FUNCTION sync_levels_from_ledger()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO inventory_levels (
        tenant_id, variant_id, location_id, lot_id, lpn_id, on_hand, allocated, owner_customer_id
    )
    VALUES (
        NEW.tenant_id, NEW.variant_id, NEW.location_id, NEW.lot_id, NEW.lpn_id,
        NEW.quantity_delta, 0, NEW.owner_customer_id
    )
    ON CONFLICT (tenant_id, variant_id, location_id, lot_id, lpn_id)
    DO UPDATE SET
        on_hand = inventory_levels.on_hand + EXCLUDED.on_hand,
        owner_customer_id = COALESCE(EXCLUDED.owner_customer_id, inventory_levels.owner_customer_id),
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION sync_levels_from_allocations()
RETURNS TRIGGER AS $$
DECLARE
    v_tenant UUID;
    v_variant UUID;
    v_location UUID;
    v_lot UUID;
    v_delta NUMERIC(19,4);
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status = 'ACTIVE' THEN
            v_tenant := NEW.tenant_id; v_variant := NEW.variant_id;
            v_location := NEW.location_id; v_lot := NEW.lot_id; v_delta := NEW.quantity;
        ELSE
            RETURN NEW;
        END IF;
    ELSIF TG_OP = 'UPDATE' THEN
        v_tenant := NEW.tenant_id; v_variant := NEW.variant_id;
        v_location := NEW.location_id; v_lot := NEW.lot_id;
        IF OLD.status = 'ACTIVE' AND NEW.status <> 'ACTIVE' THEN
            v_delta := -OLD.quantity;
        ELSIF OLD.status <> 'ACTIVE' AND NEW.status = 'ACTIVE' THEN
            v_delta := NEW.quantity;
        ELSIF OLD.status = 'ACTIVE' AND NEW.status = 'ACTIVE' THEN
            v_delta := NEW.quantity - OLD.quantity;
        ELSE
            RETURN NEW;
        END IF;
    ELSIF TG_OP = 'DELETE' THEN
        IF OLD.status = 'ACTIVE' THEN
            v_tenant := OLD.tenant_id; v_variant := OLD.variant_id;
            v_location := OLD.location_id; v_lot := OLD.lot_id; v_delta := -OLD.quantity;
        ELSE
            RETURN OLD;
        END IF;
    END IF;

    INSERT INTO inventory_levels (tenant_id, variant_id, location_id, lot_id, lpn_id, on_hand, allocated)
    VALUES (v_tenant, v_variant, v_location, v_lot, NULL, 0, v_delta)
    ON CONFLICT (tenant_id, variant_id, location_id, lot_id, lpn_id)
    DO UPDATE SET allocated = inventory_levels.allocated + EXCLUDED.allocated,
                  updated_at = NOW();

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE picking_tasks
    ADD COLUMN IF NOT EXISTS tote_identifier VARCHAR(20);

COMMENT ON TABLE license_plates IS 'Bulk material handling license plate numbers (LPNs)';
COMMENT ON COLUMN picking_tasks.tote_identifier IS 'MIB tote routing label (e.g. Tote A) per sales order in a wave';
