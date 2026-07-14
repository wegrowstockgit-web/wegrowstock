-- V041: Permit quantity-neutral landed-cost ADJUST rows (on_hand unchanged)

ALTER TABLE inventory_ledger DROP CONSTRAINT IF EXISTS inventory_ledger_quantity_delta_check;
ALTER TABLE inventory_ledger
    ADD CONSTRAINT inventory_ledger_quantity_delta_check
    CHECK (
        quantity_delta <> 0
        OR (movement_type = 'ADJUST' AND reason_code = 'LANDED_COST_ALLOCATION')
    );
