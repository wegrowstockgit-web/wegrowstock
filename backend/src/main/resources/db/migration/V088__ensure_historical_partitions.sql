-- Ensure 12 months of historical + 6 forward partitions for telemetry tables.
-- Safe / idempotent for environments that already applied V087 with a shorter window.

SELECT ensure_monthly_partitions(
    'inventory_ledger'::regclass,
    (date_trunc('month', NOW()) - INTERVAL '12 months')::date,
    19
);

SELECT ensure_monthly_partitions(
    'audit_log'::regclass,
    (date_trunc('month', NOW()) - INTERVAL '12 months')::date,
    19
);
