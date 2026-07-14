-- Track 1: spatial sequence_index for path optimization
-- Track 2: document_url on AP OCR ingestions

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS sequence_index INT NOT NULL DEFAULT 0;

-- Backfill spatial sort key from existing path segments (WAREHOUSE > ZONE > AISLE > BIN)
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY path ASC) AS seq
    FROM locations
)
UPDATE locations l
SET sequence_index = ranked.seq
FROM ranked
WHERE l.id = ranked.id;

CREATE INDEX IF NOT EXISTS idx_locations_tenant_path_seq
    ON locations (tenant_id, path, sequence_index);

ALTER TABLE supplier_invoice_ingestions
    ADD COLUMN IF NOT EXISTS document_url TEXT;

-- Unique invoice factoring row (invoice_id globally unique within factoring ledger)
CREATE UNIQUE INDEX IF NOT EXISTS uq_factored_invoices_invoice_id
    ON factored_invoices (invoice_id);
