-- Document AI AP ingestions: raw file → MinIO → async parse → STAGED for office review

CREATE TABLE ap_invoice_ingestions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    file_storage_key    VARCHAR(512) NOT NULL,
    ingestion_status    VARCHAR(50) NOT NULL DEFAULT 'PROCESSING'
        CHECK (ingestion_status IN ('PROCESSING', 'STAGED', 'FAILED')),
    parsed_metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
    matched_purchase_order_id UUID REFERENCES purchase_orders(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ap_invoice_ingestions_tenant_status
    ON ap_invoice_ingestions (tenant_id, ingestion_status);

CREATE TRIGGER ap_invoice_ingestions_updated_at BEFORE UPDATE ON ap_invoice_ingestions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE ap_invoice_ingestions ENABLE ROW LEVEL SECURITY;
ALTER TABLE ap_invoice_ingestions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ap_invoice_ingestions
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON ap_invoice_ingestions TO app_user;
GRANT SELECT ON ap_invoice_ingestions TO app_owner;
