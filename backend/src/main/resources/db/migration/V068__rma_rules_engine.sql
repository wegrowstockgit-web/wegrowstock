-- V068: Self-serve RMA rules engine (auto-approve vs pending review)
-- (Prompt referenced V059; that slot is already used by ledger_reversal_guards.)

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS rma_requires_review BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS rma_auto_approve_max_value NUMERIC(19,4) NOT NULL DEFAULT 100.00;

COMMENT ON COLUMN product_variants.rma_requires_review IS
    'When true, portal RMAs for this variant always require office review.';
COMMENT ON COLUMN tenant_settings.rma_auto_approve_max_value IS
    'Max RMA merchandise value eligible for automatic approval + prepaid return label.';

ALTER TABLE returns DROP CONSTRAINT IF EXISTS returns_status_check;
ALTER TABLE returns
    ADD CONSTRAINT returns_status_check
    CHECK (status IN (
        'REQUESTED',
        'PENDING_REVIEW',
        'APPROVED',
        'EXPECTED',
        'RECEIVED',
        'CLOSED',
        'REJECTED'
    ));

ALTER TABLE returns
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS return_label_url VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS estimated_label_cost NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS label_purchase_mode VARCHAR(20)
        CHECK (label_purchase_mode IS NULL OR label_purchase_mode IN ('SYSTEM', 'CUSTOMER', 'NONE'));

ALTER TABLE return_lines
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS media_object_id UUID REFERENCES media_objects(id);
