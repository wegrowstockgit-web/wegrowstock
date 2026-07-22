-- Enterprise workflow extensions: alt barcodes, ASN IN_TRANSIT, quarantine returns, split invoices.

-- Alternative scannable barcodes mapped to a master variant (UPC/EAN/vendor labels).
CREATE TABLE variant_barcodes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id  UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    barcode     VARCHAR(100) NOT NULL,
    symbology   VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    is_primary  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, barcode)
);

CREATE TRIGGER variant_barcodes_updated_at BEFORE UPDATE ON variant_barcodes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_variant_barcodes_variant ON variant_barcodes(variant_id);

ALTER TABLE variant_barcodes ENABLE ROW LEVEL SECURITY;
ALTER TABLE variant_barcodes FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON variant_barcodes;
CREATE POLICY tenant_isolation ON variant_barcodes
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON variant_barcodes TO app_user;

-- Backfill primary barcodes from product_variants.barcode
INSERT INTO variant_barcodes (id, tenant_id, variant_id, barcode, symbology, is_primary)
SELECT gen_random_uuid(), tenant_id, id, barcode, 'UPC', TRUE
FROM product_variants
WHERE barcode IS NOT NULL AND barcode <> ''
ON CONFLICT (tenant_id, barcode) DO NOTHING;

-- PO ASN: allow IN_TRANSIT between SUBMITTED and physical receipt
ALTER TABLE purchase_orders DROP CONSTRAINT IF EXISTS purchase_orders_status_check;
ALTER TABLE purchase_orders ADD CONSTRAINT purchase_orders_status_check
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'IN_TRANSIT', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CLOSED', 'CANCELLED'));

-- Returns: quarantine gate before ATP
ALTER TABLE return_lines DROP CONSTRAINT IF EXISTS return_lines_disposition_check;
ALTER TABLE return_lines ADD CONSTRAINT return_lines_disposition_check
    CHECK (disposition IS NULL OR disposition IN ('QUARANTINE', 'RESTOCK', 'SCRAP', 'REPAIR'));

-- Split invoicing: optional shipment link; multiple invoices per SO allowed
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS shipment_id UUID REFERENCES shipments(id);
CREATE INDEX IF NOT EXISTS idx_invoices_shipment ON invoices(shipment_id);
CREATE INDEX IF NOT EXISTS idx_invoices_sales_order ON invoices(sales_order_id);
