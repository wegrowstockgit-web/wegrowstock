-- V101: Return to Vendor (RTV) orders and chargeback lines.

CREATE TABLE IF NOT EXISTS rtv_orders (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    supplier_id              UUID NOT NULL REFERENCES suppliers(id),
    purchase_order_id        UUID REFERENCES purchase_orders(id),
    number                   VARCHAR(64) NOT NULL,
    status                   VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    debit_memo_number        VARCHAR(64),
    total_chargeback_amount  NUMERIC(19, 4) NOT NULL DEFAULT 0,
    carrier                  VARCHAR(100),
    tracking_number          VARCHAR(100),
    exception_id             UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, number)
);

CREATE TABLE IF NOT EXISTS rtv_order_lines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    rtv_order_id    UUID NOT NULL REFERENCES rtv_orders(id) ON DELETE CASCADE,
    variant_id      UUID NOT NULL REFERENCES product_variants(id),
    lot_id          UUID,
    location_id     UUID,
    qty_returned    NUMERIC(19, 4) NOT NULL,
    unit_cost       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    reason_code     VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rtv_orders_tenant_status ON rtv_orders (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_rtv_lines_order ON rtv_order_lines (tenant_id, rtv_order_id);

ALTER TABLE rtv_orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE rtv_orders FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON rtv_orders;
CREATE POLICY tenant_isolation ON rtv_orders
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE rtv_order_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE rtv_order_lines FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON rtv_order_lines;
CREATE POLICY tenant_isolation ON rtv_order_lines
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON rtv_orders TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON rtv_orders TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON rtv_order_lines TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON rtv_order_lines TO app_owner;
