-- V069: Floor labor velocity (LMS) — claim/complete timestamps on picking batches
-- Also allow synthetic PICK ledger rows for analytics / future floor instrumentation.

ALTER TABLE picking_batches
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_picking_batches_assignee_claimed
    ON picking_batches (tenant_id, assigned_user_id, claimed_at)
    WHERE assigned_user_id IS NOT NULL;

COMMENT ON COLUMN picking_batches.claimed_at IS
    'When the picker claimed the wave/batch (active wave clock start).';
COMMENT ON COLUMN picking_batches.completed_at IS
    'When the batch finished (all tasks picked or wave completed).';

-- Backfill: claimed ≈ created_at for already-assigned released/completed batches
UPDATE picking_batches
SET claimed_at = COALESCE(claimed_at, created_at)
WHERE assigned_user_id IS NOT NULL
  AND claimed_at IS NULL
  AND status IN ('RELEASED', 'COMPLETED');

UPDATE picking_batches
SET completed_at = COALESCE(completed_at, updated_at)
WHERE status = 'COMPLETED'
  AND completed_at IS NULL;

ALTER TABLE inventory_ledger DROP CONSTRAINT IF EXISTS inventory_ledger_movement_type_check;
ALTER TABLE inventory_ledger ADD CONSTRAINT inventory_ledger_movement_type_check
    CHECK (movement_type IN (
        'RECEIVE', 'SHIP', 'PICK', 'ADJUST',
        'TRANSFER_IN', 'TRANSFER_OUT', 'ASSEMBLY_IN', 'ASSEMBLY_OUT'
    ));
