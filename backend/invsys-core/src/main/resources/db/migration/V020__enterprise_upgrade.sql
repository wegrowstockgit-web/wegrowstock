-- Track 7: Picking waves & batches
CREATE TABLE picking_waves (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'RELEASED', 'COMPLETED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER picking_waves_updated_at BEFORE UPDATE ON picking_waves
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE picking_batches (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    wave_id          UUID NOT NULL REFERENCES picking_waves(id) ON DELETE CASCADE,
    assigned_user_id UUID REFERENCES users(id),
    zone_id          UUID REFERENCES locations(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'RELEASED', 'COMPLETED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER picking_batches_updated_at BEFORE UPDATE ON picking_batches
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE picking_tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    batch_id        UUID NOT NULL REFERENCES picking_batches(id) ON DELETE CASCADE,
    allocation_id   UUID NOT NULL REFERENCES allocations(id),
    location_path   VARCHAR(500) NOT NULL,
    sequence_order  INT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PICKED', 'SKIPPED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_picking_tasks_batch_seq ON picking_tasks(batch_id, sequence_order);
CREATE TRIGGER picking_tasks_updated_at BEFORE UPDATE ON picking_tasks
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Track 8: B2B credit lines
CREATE TABLE customer_credit_lines (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id       UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    credit_limit      NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (credit_limit >= 0),
    available_credit  NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (available_credit >= 0),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, customer_id)
);

CREATE TRIGGER customer_credit_lines_updated_at BEFORE UPDATE ON customer_credit_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Track 9: Demand forecasting
CREATE TABLE demand_forecasts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id          UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    recommended_po_qty  NUMERIC(19,4) NOT NULL DEFAULT 0,
    velocity_30d        NUMERIC(19,4) NOT NULL DEFAULT 0,
    calculated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, variant_id)
);

CREATE TRIGGER demand_forecasts_updated_at BEFORE UPDATE ON demand_forecasts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Track 9: EDI foundation
CREATE TABLE edi_trading_partners (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    customer_id UUID REFERENCES customers(id),
    supplier_id UUID REFERENCES suppliers(id),
    as2_id      VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, as2_id)
);

CREATE TRIGGER edi_trading_partners_updated_at BEFORE UPDATE ON edi_trading_partners
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE edi_transactions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    partner_id       UUID NOT NULL REFERENCES edi_trading_partners(id) ON DELETE CASCADE,
    transaction_type VARCHAR(10) NOT NULL CHECK (transaction_type IN ('850', '855', '856', '810')),
    payload          TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER edi_transactions_updated_at BEFORE UPDATE ON edi_transactions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- RLS on new tenant-scoped tables
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'picking_waves', 'picking_batches', 'picking_tasks',
        'customer_credit_lines', 'demand_forecasts',
        'edi_trading_partners', 'edi_transactions'
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

GRANT SELECT, INSERT, UPDATE, DELETE ON
    picking_waves, picking_batches, picking_tasks,
    customer_credit_lines, demand_forecasts,
    edi_trading_partners, edi_transactions
TO app_user;
