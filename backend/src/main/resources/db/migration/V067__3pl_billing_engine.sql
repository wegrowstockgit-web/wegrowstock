-- V067: Hybrid 3PL multi-owner billing (SLA + daily storage accruals)
-- (Prompt referenced V058; that slot is already used by ap_invoice_ingestions.)

ALTER TABLE inventory_levels
    ADD COLUMN IF NOT EXISTS owner_customer_id UUID REFERENCES customers(id);

CREATE INDEX IF NOT EXISTS idx_inventory_levels_owner
    ON inventory_levels (tenant_id, owner_customer_id)
    WHERE owner_customer_id IS NOT NULL;

ALTER TABLE inventory_ledger
    ADD COLUMN IF NOT EXISTS owner_customer_id UUID REFERENCES customers(id);

-- Propagate ownership onto levels when ledger rows carry owner_customer_id
CREATE OR REPLACE FUNCTION sync_levels_from_ledger()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO inventory_levels (
        tenant_id, variant_id, location_id, lot_id, on_hand, allocated, owner_customer_id
    )
    VALUES (
        NEW.tenant_id, NEW.variant_id, NEW.location_id, NEW.lot_id,
        NEW.quantity_delta, 0, NEW.owner_customer_id
    )
    ON CONFLICT (tenant_id, variant_id, location_id, lot_id)
    DO UPDATE SET
        on_hand = inventory_levels.on_hand + EXCLUDED.on_hand,
        owner_customer_id = COALESCE(EXCLUDED.owner_customer_id, inventory_levels.owner_customer_id),
        updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Optional dedicated pallet positions for PALLET_POSITION SLAs
ALTER TABLE locations DROP CONSTRAINT IF EXISTS locations_type_check;
ALTER TABLE locations ADD CONSTRAINT locations_type_check
    CHECK (type IN ('WAREHOUSE', 'ZONE', 'AISLE', 'BIN', 'PALLET', 'QUARANTINE', 'VEHICLE'));

CREATE TABLE billing_slas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id         UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    storage_mode        VARCHAR(20) NOT NULL DEFAULT 'PALLET_POSITION'
        CHECK (storage_mode IN ('PALLET_POSITION', 'CUBIC_VOLUME')),
    rate_per_unit       NUMERIC(19,6) NOT NULL DEFAULT 0 CHECK (rate_per_unit >= 0),
    pick_fee_per_item   NUMERIC(19,6) NOT NULL DEFAULT 0 CHECK (pick_fee_per_item >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, customer_id)
);

CREATE TRIGGER billing_slas_updated_at BEFORE UPDATE ON billing_slas
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE billing_slas ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing_slas FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON billing_slas
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON billing_slas TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON billing_slas TO app_owner;

CREATE TABLE billing_accruals (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id     UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    accrual_date    DATE NOT NULL,
    amount          NUMERIC(19,4) NOT NULL CHECK (amount >= 0),
    description     TEXT NOT NULL DEFAULT '',
    status          VARCHAR(20) NOT NULL DEFAULT 'UNBILLED'
        CHECK (status IN ('UNBILLED', 'BILLED', 'VOID')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, customer_id, accrual_date, description)
);

CREATE INDEX idx_billing_accruals_tenant_customer_status
    ON billing_accruals (tenant_id, customer_id, status, accrual_date DESC);

CREATE TRIGGER billing_accruals_updated_at BEFORE UPDATE ON billing_accruals
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE billing_accruals ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing_accruals FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON billing_accruals
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON billing_accruals TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON billing_accruals TO app_owner;
