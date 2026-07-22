-- Maintain inventory_levels from ledger inserts
CREATE OR REPLACE FUNCTION sync_levels_from_ledger()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO inventory_levels (tenant_id, variant_id, location_id, lot_id, on_hand, allocated)
    VALUES (NEW.tenant_id, NEW.variant_id, NEW.location_id, NEW.lot_id, NEW.quantity_delta, 0)
    ON CONFLICT (tenant_id, variant_id, location_id, lot_id)
    DO UPDATE SET on_hand = inventory_levels.on_hand + EXCLUDED.on_hand,
                  updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_sync_levels AFTER INSERT ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION sync_levels_from_ledger();

-- Maintain allocated totals from allocations changes
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

    INSERT INTO inventory_levels (tenant_id, variant_id, location_id, lot_id, on_hand, allocated)
    VALUES (v_tenant, v_variant, v_location, v_lot, 0, v_delta)
    ON CONFLICT (tenant_id, variant_id, location_id, lot_id)
    DO UPDATE SET allocated = inventory_levels.allocated + EXCLUDED.allocated,
                  updated_at = NOW();

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_allocations_sync_levels
    AFTER INSERT OR UPDATE OR DELETE ON allocations
    FOR EACH ROW EXECUTE FUNCTION sync_levels_from_allocations();
