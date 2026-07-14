CREATE TABLE inventory_ledger (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id              UUID NOT NULL REFERENCES product_variants(id),
    location_id             UUID NOT NULL REFERENCES locations(id),
    lot_id                  UUID REFERENCES lots(id),
    movement_type           VARCHAR(20) NOT NULL CHECK (movement_type IN ('RECEIVE', 'SHIP', 'ADJUST', 'TRANSFER_IN', 'TRANSFER_OUT')),
    quantity_delta          NUMERIC(19,4) NOT NULL CHECK (quantity_delta <> 0),
    reason_code             VARCHAR(50),
    reference_type          VARCHAR(50),
    reference_id            UUID,
    transfer_group_id       UUID,
    reversal_of_ledger_id   UUID REFERENCES inventory_ledger(id),
    created_by              UUID REFERENCES users(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ledger_tenant_variant_loc ON inventory_ledger(tenant_id, variant_id, location_id, created_at);
CREATE INDEX idx_ledger_transfer_group ON inventory_ledger(transfer_group_id) WHERE transfer_group_id IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'inventory_ledger is append-only: UPDATE and DELETE are forbidden';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_ledger_no_update BEFORE UPDATE ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();
CREATE TRIGGER inventory_ledger_no_delete BEFORE DELETE ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TABLE allocations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sales_order_line_id UUID,
    variant_id          UUID NOT NULL REFERENCES product_variants(id),
    location_id         UUID NOT NULL REFERENCES locations(id),
    lot_id              UUID REFERENCES lots(id),
    quantity            NUMERIC(19,4) NOT NULL CHECK (quantity > 0),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED')),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_allocations_active ON allocations(tenant_id, variant_id, location_id) WHERE status = 'ACTIVE';
CREATE TRIGGER allocations_updated_at BEFORE UPDATE ON allocations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE inventory_levels (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id  UUID NOT NULL REFERENCES product_variants(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    lot_id      UUID REFERENCES lots(id),
    on_hand     NUMERIC(19,4) NOT NULL DEFAULT 0,
    allocated   NUMERIC(19,4) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, variant_id, location_id, lot_id)
);

CREATE INDEX idx_levels_variant ON inventory_levels(tenant_id, variant_id);
CREATE TRIGGER inventory_levels_updated_at BEFORE UPDATE ON inventory_levels
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE cycle_counts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES locations(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    notes       TEXT,
    created_by  UUID REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER cycle_counts_updated_at BEFORE UPDATE ON cycle_counts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE cycle_count_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    cycle_count_id  UUID NOT NULL REFERENCES cycle_counts(id) ON DELETE CASCADE,
    variant_id      UUID NOT NULL REFERENCES product_variants(id),
    lot_id          UUID REFERENCES lots(id),
    expected_qty    NUMERIC(19,4) NOT NULL DEFAULT 0,
    counted_qty     NUMERIC(19,4),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER cycle_count_lines_updated_at BEFORE UPDATE ON cycle_count_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
