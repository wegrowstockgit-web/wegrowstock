-- V046: Dual-tier enterprise media — transactional evidence (catalog product_media/avatar remain V044)

CREATE TABLE IF NOT EXISTS transaction_media (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    entity_type  VARCHAR(50) NOT NULL,
    entity_id    UUID NOT NULL,
    url          VARCHAR(1024) NOT NULL,
    captured_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_transaction_media_entity_type CHECK (entity_type IN (
        'RECEIPT', 'RMA', 'LEDGER_ENTRY', 'PURCHASE_ORDER_LINE', 'RETURN_LINE', 'FULFILLMENT_SCAN'
    ))
);

CREATE INDEX IF NOT EXISTS idx_transaction_media_entity
    ON transaction_media (tenant_id, entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_transaction_media_created
    ON transaction_media (tenant_id, created_at DESC);

ALTER TABLE transaction_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE transaction_media FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON transaction_media;
CREATE POLICY tenant_isolation ON transaction_media
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON transaction_media TO app_user;
GRANT SELECT ON transaction_media TO app_owner;

DROP TRIGGER IF EXISTS transaction_media_updated_at ON transaction_media;
CREATE TRIGGER transaction_media_updated_at BEFORE UPDATE ON transaction_media
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE transaction_media IS 'Transactional / floor evidence photos (RECEIPT, RMA, etc.). Catalog images stay in product_media.';
