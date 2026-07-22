-- V071: Blind cycle counting + automated variance escalation
-- Prompt referenced V061; that slot is tenant_subscription_lockdown.

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS blind_cycle_counts BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS max_auto_adjust_value NUMERIC(19, 4) NOT NULL DEFAULT 100.00;

ALTER TABLE cycle_count_lines
    ADD COLUMN IF NOT EXISTS variance_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

ALTER TABLE cycle_count_lines
    ADD COLUMN IF NOT EXISTS financial_impact NUMERIC(19, 4);

COMMENT ON COLUMN tenant_settings.blind_cycle_counts IS
    'When true, floor scanners hide expected_qty to prevent confirmation bias';
COMMENT ON COLUMN tenant_settings.max_auto_adjust_value IS
    'Max absolute financial impact ($) auto-adjusted without manager review';
COMMENT ON COLUMN cycle_count_lines.variance_status IS
    'PENDING | AUTO_APPROVED | PENDING_MANAGER_REVIEW | APPROVED | RECOUNT_REQUESTED';

-- Backfill lines already counted as exact matches before variance escalation existed.
UPDATE cycle_count_lines
SET variance_status = 'AUTO_APPROVED',
    financial_impact = 0
WHERE counted_qty IS NOT NULL
  AND counted_qty = expected_qty
  AND variance_status = 'PENDING';
