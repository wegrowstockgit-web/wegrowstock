-- V111: Offline-first Retail POS addon.
-- Idempotent receipt ingest + ENTERPRISE commercial bundle includes RETAIL_POS.

CREATE TABLE pos_synced_receipts (
    receipt_id          UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    store_location_id   UUID NOT NULL,
    tender_type         VARCHAR(40),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, receipt_id)
);

CREATE INDEX idx_pos_synced_receipts_store
    ON pos_synced_receipts (tenant_id, store_location_id, created_at DESC);

ALTER TABLE pos_synced_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE pos_synced_receipts FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON pos_synced_receipts
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT ON pos_synced_receipts TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON pos_synced_receipts TO app_owner;

COMMENT ON TABLE pos_synced_receipts IS
    'Idempotency keys for offline POS receipt sync; inventory deduction is queued via inventory_level_deltas.';

UPDATE platform_tier_definitions
SET default_modules = '["CORE","SHOPIFY","ACCOUNTING","ADVANCED_FULFILLMENT","MANUFACTURING","DOCUMENTS","MRP","B2B_SHOWROOM","FINTECH","MESH_NETWORK","RTLS_TELEMETRY","AI_COPILOT","RETAIL_POS"]'::jsonb,
    updated_at = NOW()
WHERE tier_code = 'ENTERPRISE';
