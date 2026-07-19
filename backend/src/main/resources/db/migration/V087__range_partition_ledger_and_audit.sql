-- Range-partition inventory_ledger and audit_log by created_at (monthly).
-- PK becomes (id, created_at). Compound index (tenant_id, created_at, id) for tenant scans.
-- ensure_monthly_partitions() creates inventory_ledger_yYYYYmMM / audit_log_yYYYYmMM ahead of time.

CREATE OR REPLACE FUNCTION ensure_monthly_partitions(
    p_parent regclass,
    p_from date,
    p_months integer DEFAULT 3
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
SET row_security = off
AS $$
DECLARE
    parent_name text := p_parent::text;
    bare_name text;
    start_month date := date_trunc('month', p_from)::date;
    i integer;
    part_start date;
    part_end date;
    part_name text;
BEGIN
    IF p_months < 1 THEN
        RAISE EXCEPTION 'p_months must be >= 1';
    END IF;

    bare_name := CASE
        WHEN parent_name LIKE '%.%' THEN split_part(parent_name, '.', 2)
        ELSE parent_name
    END;
    bare_name := trim(both '"' from bare_name);

    FOR i IN 0..(p_months - 1) LOOP
        part_start := (start_month + (i || ' months')::interval)::date;
        part_end := (part_start + '1 month'::interval)::date;
        part_name := format(
            '%s_y%sm%s',
            bare_name,
            to_char(part_start, 'YYYY'),
            to_char(part_start, 'MM')
        );
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
            part_name,
            p_parent,
            part_start,
            part_end
        );
    END LOOP;
END;
$$;

COMMENT ON FUNCTION ensure_monthly_partitions(regclass, date, integer) IS
    'Creates monthly RANGE partitions named <parent>_yYYYYmMM for the given parent table';

REVOKE ALL ON FUNCTION ensure_monthly_partitions(regclass, date, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION ensure_monthly_partitions(regclass, date, integer) TO app_user, app_owner;

-- ---------------------------------------------------------------------------
-- inventory_ledger
-- ---------------------------------------------------------------------------
ALTER TABLE inventory_ledger RENAME TO inventory_ledger_legacy;
-- Constraint/index names are schema-scoped; free the canonical names before recreate.
ALTER TABLE inventory_ledger_legacy RENAME CONSTRAINT inventory_ledger_pkey TO inventory_ledger_legacy_pkey;
ALTER TABLE inventory_ledger_legacy RENAME CONSTRAINT inventory_ledger_quantity_delta_check TO inventory_ledger_legacy_qty_chk;
ALTER TABLE inventory_ledger_legacy RENAME CONSTRAINT inventory_ledger_movement_type_check TO inventory_ledger_legacy_mv_chk;

CREATE TABLE inventory_ledger (
    LIKE inventory_ledger_legacy INCLUDING DEFAULTS INCLUDING COMMENTS
) PARTITION BY RANGE (created_at);

ALTER TABLE inventory_ledger
    ADD CONSTRAINT inventory_ledger_pkey PRIMARY KEY (id, created_at);

ALTER TABLE inventory_ledger
    ADD CONSTRAINT inventory_ledger_quantity_delta_check
    CHECK (
        quantity_delta <> 0
        OR (
            movement_type = 'ADJUST'
            AND reason_code IN ('LANDED_COST_ALLOCATION', 'ERROR_CORRECTION')
        )
    );

ALTER TABLE inventory_ledger
    ADD CONSTRAINT inventory_ledger_movement_type_check
    CHECK (movement_type IN (
        'RECEIVE', 'SHIP', 'PICK', 'ADJUST',
        'TRANSFER_IN', 'TRANSFER_OUT', 'ASSEMBLY_IN', 'ASSEMBLY_OUT'
    ));

DO $$
DECLARE
    min_created timestamptz;
    max_created timestamptz;
    from_month date;
    months_needed integer;
BEGIN
    SELECT min(created_at), max(created_at)
    INTO min_created, max_created
    FROM inventory_ledger_legacy;

    from_month := date_trunc(
        'month',
        COALESCE(min_created, NOW())
    )::date;

    months_needed := GREATEST(
        3,
        (
            EXTRACT(YEAR FROM date_trunc('month', COALESCE(max_created, NOW()) + interval '3 months'))::integer * 12
            + EXTRACT(MONTH FROM date_trunc('month', COALESCE(max_created, NOW()) + interval '3 months'))::integer
        ) - (
            EXTRACT(YEAR FROM from_month)::integer * 12
            + EXTRACT(MONTH FROM from_month)::integer
        ) + 1
    );

    PERFORM ensure_monthly_partitions('inventory_ledger'::regclass, from_month, months_needed);
END $$;

INSERT INTO inventory_ledger
SELECT * FROM inventory_ledger_legacy;

DROP TABLE inventory_ledger_legacy;

CREATE INDEX IF NOT EXISTS idx_inventory_ledger_id
    ON inventory_ledger (id);
CREATE INDEX IF NOT EXISTS idx_ledger_tenant_created_id
    ON inventory_ledger (tenant_id, created_at, id);
CREATE INDEX IF NOT EXISTS idx_ledger_tenant_variant_loc
    ON inventory_ledger (tenant_id, variant_id, location_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ledger_transfer_group
    ON inventory_ledger (transfer_group_id)
    WHERE transfer_group_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_ledger_serial
    ON inventory_ledger (serial_number_id)
    WHERE serial_number_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_ledger_lpn
    ON inventory_ledger (tenant_id, lpn_id)
    WHERE lpn_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_ledger_vendor_lot_captured
    ON inventory_ledger ((metadata ->> 'vendor_lot_captured'));
-- Partition-safe uniqueness (must include range key)
CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_ledger_reversal_of
    ON inventory_ledger (reversal_of_ledger_id, created_at)
    WHERE reversal_of_ledger_id IS NOT NULL;

CREATE OR REPLACE FUNCTION prevent_ledger_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'inventory_ledger is append-only: UPDATE and DELETE are forbidden';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_ledger_no_update BEFORE UPDATE ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();
CREATE TRIGGER inventory_ledger_no_delete BEFORE DELETE ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();

CREATE TRIGGER trg_ledger_sync_levels AFTER INSERT ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION sync_levels_from_ledger();

CREATE TRIGGER inventory_ledger_serial_outbound
    BEFORE INSERT ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION validate_serial_outbound();

ALTER TABLE inventory_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_ledger FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON inventory_ledger;
CREATE POLICY tenant_isolation ON inventory_ledger
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

GRANT SELECT, INSERT ON inventory_ledger TO app_user;

-- ---------------------------------------------------------------------------
-- audit_log
-- ---------------------------------------------------------------------------
ALTER TABLE audit_log RENAME TO audit_log_legacy;
ALTER TABLE audit_log_legacy RENAME CONSTRAINT audit_log_pkey TO audit_log_legacy_pkey;

CREATE TABLE audit_log (
    LIKE audit_log_legacy INCLUDING DEFAULTS INCLUDING COMMENTS
) PARTITION BY RANGE (created_at);

ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id, created_at);

DO $$
DECLARE
    min_created timestamptz;
    max_created timestamptz;
    from_month date;
    months_needed integer;
BEGIN
    SELECT min(created_at), max(created_at)
    INTO min_created, max_created
    FROM audit_log_legacy;

    from_month := date_trunc(
        'month',
        COALESCE(min_created, NOW())
    )::date;

    months_needed := GREATEST(
        3,
        (
            EXTRACT(YEAR FROM date_trunc('month', COALESCE(max_created, NOW()) + interval '3 months'))::integer * 12
            + EXTRACT(MONTH FROM date_trunc('month', COALESCE(max_created, NOW()) + interval '3 months'))::integer
        ) - (
            EXTRACT(YEAR FROM from_month)::integer * 12
            + EXTRACT(MONTH FROM from_month)::integer
        ) + 1
    );

    PERFORM ensure_monthly_partitions('audit_log'::regclass, from_month, months_needed);
END $$;

INSERT INTO audit_log
SELECT * FROM audit_log_legacy;

DROP TABLE audit_log_legacy;

CREATE INDEX IF NOT EXISTS idx_audit_log_id
    ON audit_log (id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_created_id
    ON audit_log (tenant_id, created_at, id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_created
    ON audit_log (tenant_id, created_at DESC);

CREATE TRIGGER audit_log_updated_at BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON audit_log;
CREATE POLICY tenant_isolation ON audit_log
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.current_tenant', true)::uuid);

GRANT SELECT, INSERT ON audit_log TO app_user;
REVOKE UPDATE, DELETE ON audit_log FROM app_user;

-- Recreate purge helper against partitioned parent (DELETE by id still works).
CREATE OR REPLACE FUNCTION archive_purge_audit_logs(p_ids uuid[])
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
SET row_security = off
AS $$
DECLARE
    deleted_count integer;
BEGIN
    IF p_ids IS NULL OR cardinality(p_ids) = 0 THEN
        RETURN 0;
    END IF;

    DELETE FROM audit_log
    WHERE id = ANY (p_ids);

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$;

REVOKE ALL ON FUNCTION archive_purge_audit_logs(uuid[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION archive_purge_audit_logs(uuid[]) TO app_user, app_owner;

-- Cover 12 months of history + 6 months forward (demo seeds / late backfills).
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
