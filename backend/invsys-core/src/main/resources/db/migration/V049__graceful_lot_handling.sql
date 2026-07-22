-- V049: Graceful GS1 lot handling — sink vendor lots when variant is not lot-tracked
-- (V048 is soft-kitting / supplier portal)

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS is_lot_tracked BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE inventory_ledger
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN product_variants.is_lot_tracked IS
    'When false, scanned lot numbers are not bound to inventory_levels; captured in ledger metadata instead.';

COMMENT ON COLUMN inventory_ledger.metadata IS
    'Opaque JSON for graceful degradation (e.g. vendor_lot_captured when is_lot_tracked is false).';
