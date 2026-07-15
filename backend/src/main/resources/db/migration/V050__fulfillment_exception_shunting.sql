-- V050: Enterprise exception shunting for damaged lot-tracked barcodes
-- (V049 is graceful lot handling / ledger metadata)

ALTER TABLE allocations
    ALTER COLUMN status TYPE VARCHAR(50);

ALTER TABLE allocations DROP CONSTRAINT IF EXISTS allocations_status_check;
ALTER TABLE allocations
    ADD CONSTRAINT allocations_status_check
        CHECK (status IN (
            'ACTIVE',
            'RELEASED',
            'CONSUMED',
            'CANCELLED',
            'EXCEPTION_DAMAGED_BARCODE'
        ));

CREATE TABLE fulfillment_exceptions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    allocation_id     UUID NOT NULL REFERENCES allocations(id) ON DELETE CASCADE,
    reported_by       UUID NOT NULL REFERENCES users(id),
    warehouse_id      UUID NOT NULL REFERENCES locations(id),
    resolution_status VARCHAR(30) NOT NULL DEFAULT 'OPEN'
        CHECK (resolution_status IN ('OPEN', 'RESOLVED', 'DISCARDED')),
    metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fulfillment_exceptions_tenant_open
    ON fulfillment_exceptions (tenant_id, resolution_status, created_at DESC);

CREATE INDEX idx_fulfillment_exceptions_allocation
    ON fulfillment_exceptions (tenant_id, allocation_id);

DROP TRIGGER IF EXISTS fulfillment_exceptions_updated_at ON fulfillment_exceptions;
CREATE TRIGGER fulfillment_exceptions_updated_at BEFORE UPDATE ON fulfillment_exceptions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE fulfillment_exceptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE fulfillment_exceptions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON fulfillment_exceptions;
CREATE POLICY tenant_isolation ON fulfillment_exceptions
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON fulfillment_exceptions TO app_user;
GRANT SELECT ON fulfillment_exceptions TO app_owner;

COMMENT ON TABLE fulfillment_exceptions IS
    'Surface B damaged-barcode shunt records; allocation hold released without inventory_ledger writes.';
