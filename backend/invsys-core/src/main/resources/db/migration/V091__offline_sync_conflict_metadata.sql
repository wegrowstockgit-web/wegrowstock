-- V091: Hybrid metadata-driven offline sync conflict engine
-- Adds operator identity, action typing, form schema descriptors, and resolution audit columns.

ALTER TABLE offline_sync_conflicts
    RENAME COLUMN payload TO payload_json;

ALTER TABLE offline_sync_conflicts ADD COLUMN IF NOT EXISTS picker_user_id UUID REFERENCES users(id);
ALTER TABLE offline_sync_conflicts ADD COLUMN IF NOT EXISTS action_type VARCHAR(40);
ALTER TABLE offline_sync_conflicts ADD COLUMN IF NOT EXISTS request_url VARCHAR(512);
ALTER TABLE offline_sync_conflicts ADD COLUMN IF NOT EXISTS schema_metadata_json JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE offline_sync_conflicts ADD COLUMN IF NOT EXISTS resolved_by_user_id UUID REFERENCES users(id);
ALTER TABLE offline_sync_conflicts ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

-- Expand status vocabulary (keep legacy values for in-flight rows).
-- RESOLVED_AND_REPLAYED is 21 chars — widen beyond the original VARCHAR(20).
ALTER TABLE offline_sync_conflicts ALTER COLUMN status TYPE VARCHAR(40);

ALTER TABLE offline_sync_conflicts DROP CONSTRAINT IF EXISTS offline_sync_conflicts_status_check;
ALTER TABLE offline_sync_conflicts
    ADD CONSTRAINT offline_sync_conflicts_status_check
        CHECK (status IN (
            'PENDING',
            'DISCARDED',
            'RESOLVED_AND_REPLAYED',
            'RESOLVED',
            'DISMISSED',
            'RETRY_REQUESTED'
        ));

-- Backfill request_url / action_type from parked HTTP snapshots where possible.
UPDATE offline_sync_conflicts
SET request_url = COALESCE(request_url, payload_json->>'url')
WHERE request_url IS NULL
  AND payload_json ? 'url';

UPDATE offline_sync_conflicts
SET action_type = CASE lower(COALESCE(payload_json->'body'->>'mode', ''))
        WHEN 'receive' THEN 'INBOUND_RECEIVE'
        WHEN 'pick' THEN 'OUTBOUND_PICK'
        WHEN 'count' THEN 'CYCLE_COUNT'
        ELSE action_type
    END
WHERE action_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_offline_sync_conflicts_picker
    ON offline_sync_conflicts (tenant_id, picker_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_offline_sync_conflicts_action
    ON offline_sync_conflicts (tenant_id, action_type, status);
