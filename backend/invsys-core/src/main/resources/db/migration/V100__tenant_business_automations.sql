-- V100: Tenant business automation toggles / thresholds.
-- Note: V099 is RBAC seed; this migration adds predictive replenishment control.

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS predictive_replenishment_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS blind_cycle_counts BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS max_auto_adjust_value NUMERIC(19, 4) NOT NULL DEFAULT 100.00;

ALTER TABLE tenant_settings
    ADD COLUMN IF NOT EXISTS rma_auto_approve_max_value NUMERIC(19, 4) NOT NULL DEFAULT 100.00;
