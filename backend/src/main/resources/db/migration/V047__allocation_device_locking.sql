-- V047: Pre-emptive device locking for floor pick allocations
-- (V046 already used by enterprise_transaction_media)

ALTER TABLE allocations
    ADD COLUMN IF NOT EXISTS assigned_to_user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_allocations_assigned_user
    ON allocations (tenant_id, assigned_to_user_id)
    WHERE assigned_to_user_id IS NOT NULL AND status = 'ACTIVE';

-- Allow CANCELLED for explicit conflict messaging on offline replay
ALTER TABLE allocations DROP CONSTRAINT IF EXISTS allocations_status_check;
ALTER TABLE allocations
    ADD CONSTRAINT allocations_status_check
        CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED', 'CANCELLED'));

COMMENT ON COLUMN allocations.assigned_to_user_id IS
    'Picker who claimed this allocation via wave claim (device lock).';
