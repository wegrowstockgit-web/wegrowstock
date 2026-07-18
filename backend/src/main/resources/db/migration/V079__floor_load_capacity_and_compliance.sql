-- V079: Industrial floor-load alias + FSMA metadata-trace index
-- weight_capacity_limit (V078) remains; floor_load_capacity_lbs is the industry-standard name.

ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS floor_load_capacity_lbs NUMERIC(19, 4);

UPDATE locations
SET floor_load_capacity_lbs = weight_capacity_limit
WHERE floor_load_capacity_lbs IS NULL
  AND weight_capacity_limit IS NOT NULL;

COMMENT ON COLUMN locations.floor_load_capacity_lbs IS
    'Structural floor load capacity (lbs/sq ft or facility limit) for dock/storage allocation safety';

-- Speed FSMA recall searches when lot_id is null but AI 10 was sunk to metadata
CREATE INDEX IF NOT EXISTS idx_inventory_ledger_vendor_lot_captured
    ON inventory_ledger ((metadata ->> 'vendor_lot_captured'))
    WHERE metadata ? 'vendor_lot_captured';
