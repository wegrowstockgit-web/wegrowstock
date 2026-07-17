-- V076: Decouple inventory_levels hotspot writes from ledger inserts.
-- Ledger AFTER INSERT appends lock-free deltas; a virtual-thread worker flushes
-- them into inventory_levels in batches (FOR UPDATE SKIP LOCKED).

CREATE TABLE inventory_level_deltas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id          UUID NOT NULL,
    location_id         UUID NOT NULL,
    lot_id              UUID,
    lpn_id              UUID,
    on_hand_delta       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    owner_customer_id   UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at          TIMESTAMPTZ
);

-- Intentionally no unique constraint on business key (append-only, contention-free inserts).
-- Narrow partial index only for the flush worker claim path.
CREATE INDEX idx_inventory_level_deltas_pending
    ON inventory_level_deltas (created_at)
    WHERE applied_at IS NULL;

ALTER TABLE inventory_level_deltas ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_level_deltas FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON inventory_level_deltas
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

-- Cross-tenant flush worker (app_owner / bootstrap DS), same pattern as outbox.
CREATE POLICY bootstrap_deltas_select ON inventory_level_deltas
    FOR SELECT TO app_owner
    USING (true);

CREATE POLICY bootstrap_deltas_update ON inventory_level_deltas
    FOR UPDATE TO app_owner
    USING (true)
    WITH CHECK (true);

CREATE POLICY bootstrap_levels_write ON inventory_levels
    FOR ALL TO app_owner
    USING (true)
    WITH CHECK (true);

GRANT SELECT, INSERT ON inventory_level_deltas TO app_user;
GRANT SELECT, INSERT, UPDATE ON inventory_level_deltas TO app_owner;
GRANT SELECT, INSERT, UPDATE ON inventory_levels TO app_owner;

-- Ledger inserts no longer touch inventory_levels synchronously.
CREATE OR REPLACE FUNCTION sync_levels_from_ledger()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO inventory_level_deltas (
        tenant_id, variant_id, location_id, lot_id, lpn_id, on_hand_delta, owner_customer_id
    )
    VALUES (
        NEW.tenant_id, NEW.variant_id, NEW.location_id, NEW.lot_id, NEW.lpn_id,
        NEW.quantity_delta, NEW.owner_customer_id
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON TABLE inventory_level_deltas IS
    'Lock-free on_hand deltas from ledger inserts; flushed asynchronously into inventory_levels';
