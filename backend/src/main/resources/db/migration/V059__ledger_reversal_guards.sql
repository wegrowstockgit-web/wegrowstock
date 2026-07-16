-- V059: Unique reversal target + allow zero-qty ERROR_CORRECTION compensating rows

CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_ledger_reversal_of
    ON inventory_ledger (reversal_of_ledger_id)
    WHERE reversal_of_ledger_id IS NOT NULL;

ALTER TABLE inventory_ledger DROP CONSTRAINT IF EXISTS inventory_ledger_quantity_delta_check;
ALTER TABLE inventory_ledger
    ADD CONSTRAINT inventory_ledger_quantity_delta_check
    CHECK (
        quantity_delta <> 0
        OR (
            movement_type = 'ADJUST'
            AND reason_code IN ('LANDED_COST_ALLOCATION', 'ERROR_CORRECTION')
        )
    );
