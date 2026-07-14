CREATE TABLE returns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sales_order_id  UUID NOT NULL REFERENCES sales_orders(id),
    number          VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
        CHECK (status IN ('REQUESTED', 'APPROVED', 'RECEIVED', 'CLOSED', 'REJECTED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, number)
);

CREATE TRIGGER returns_updated_at BEFORE UPDATE ON returns
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE return_lines (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    return_id           UUID NOT NULL REFERENCES returns(id) ON DELETE CASCADE,
    sales_order_line_id UUID NOT NULL REFERENCES sales_order_lines(id),
    quantity_expected   NUMERIC(19,4) NOT NULL CHECK (quantity_expected > 0),
    quantity_received   NUMERIC(19,4) NOT NULL DEFAULT 0,
    disposition         VARCHAR(50) CHECK (disposition IN ('RESTOCK', 'SCRAP', 'REPAIR')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER return_lines_updated_at BEFORE UPDATE ON return_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Global FX rates (no tenant_id, no RLS)
CREATE TABLE currency_rates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_currency CHAR(3) NOT NULL,
    to_currency   CHAR(3) NOT NULL,
    rate          NUMERIC(12,6) NOT NULL CHECK (rate > 0),
    as_of         TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (from_currency, to_currency)
);

CREATE TRIGGER currency_rates_updated_at BEFORE UPDATE ON currency_rates
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE invoices ADD COLUMN fx_rate NUMERIC(12,6) NULL;

-- RLS on returns tables
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY['returns', 'return_lines'];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true)::uuid)',
            t
        );
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON returns, return_lines TO app_user;
GRANT SELECT ON currency_rates TO app_user;
