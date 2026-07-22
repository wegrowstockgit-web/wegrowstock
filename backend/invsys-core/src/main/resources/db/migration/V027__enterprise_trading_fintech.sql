-- Enterprise trading, intelligent routing, AP OCR, EDI compliance, embedded finance

-- 2. AI demand sensing enrichment
ALTER TABLE demand_forecasts
    ADD COLUMN IF NOT EXISTS seasonality_index NUMERIC(4,2) NOT NULL DEFAULT 1.00,
    ADD COLUMN IF NOT EXISTS confidence_score NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS external_signals JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 3. AP OCR ingestion
CREATE TABLE supplier_invoice_ingestions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    purchase_order_id  UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    status             VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RECONCILED', 'CONFLICT')),
    extracted_data     JSONB NOT NULL DEFAULT '{}'::jsonb,
    match_confidence   NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supplier_invoice_ingestions_po ON supplier_invoice_ingestions(purchase_order_id);
CREATE TRIGGER supplier_invoice_ingestions_updated_at BEFORE UPDATE ON supplier_invoice_ingestions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 4. EDI document logs
CREATE TABLE edi_document_logs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    trading_partner_id  UUID NOT NULL REFERENCES edi_trading_partners(id) ON DELETE CASCADE,
    direction           VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    document_type       VARCHAR(20) NOT NULL,
    payload             TEXT NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'SENT')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_edi_document_logs_partner ON edi_document_logs(trading_partner_id, document_type);
CREATE TRIGGER edi_document_logs_updated_at BEFORE UPDATE ON edi_document_logs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 5. Embedded finance
CREATE TABLE factored_invoices (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    invoice_id            UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    advance_rate          NUMERIC(5,2) NOT NULL DEFAULT 85.00,
    discount_fee_percent  NUMERIC(5,2) NOT NULL DEFAULT 2.50,
    funding_status        VARCHAR(30) NOT NULL DEFAULT 'ELIGIBLE'
        CHECK (funding_status IN ('ELIGIBLE', 'REQUESTED', 'FUNDED', 'SETTLED')),
    escrow_payout_ref     VARCHAR(255),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, invoice_id)
);

CREATE TRIGGER factored_invoices_updated_at BEFORE UPDATE ON factored_invoices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE capital_credit_lines (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    credit_limit         NUMERIC(19,4) NOT NULL DEFAULT 0,
    outstanding_balance  NUMERIC(19,4) NOT NULL DEFAULT 0,
    interest_rate_apr    NUMERIC(5,2) NOT NULL DEFAULT 12.00,
    utilization_status   VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (utilization_status IN ('AVAILABLE', 'DRAWN', 'SUSPENDED', 'CLOSED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id)
);

CREATE TRIGGER capital_credit_lines_updated_at BEFORE UPDATE ON capital_credit_lines
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'supplier_invoice_ingestions', 'edi_document_logs',
        'factored_invoices', 'capital_credit_lines'
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

GRANT SELECT, INSERT, UPDATE, DELETE ON supplier_invoice_ingestions TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON edi_document_logs TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON factored_invoices TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON capital_credit_lines TO app_user;
