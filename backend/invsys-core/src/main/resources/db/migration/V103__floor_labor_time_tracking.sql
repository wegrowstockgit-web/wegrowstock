-- V103: Floor labor time & attendance (direct vs indirect punch clock).

CREATE TABLE IF NOT EXISTS labor_shifts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id       UUID NOT NULL REFERENCES users(id),
    warehouse_id  UUID REFERENCES locations(id),
    clock_in      TIMESTAMPTZ NOT NULL,
    clock_out     TIMESTAMPTZ,
    status        VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS labor_time_entries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    shift_id          UUID NOT NULL REFERENCES labor_shifts(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users(id),
    activity_type     VARCHAR(64) NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    units_processed   INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_labor_shifts_active_user
    ON labor_shifts (tenant_id, user_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_labor_time_entries_shift
    ON labor_time_entries (tenant_id, shift_id, started_at);

ALTER TABLE labor_shifts ENABLE ROW LEVEL SECURITY;
ALTER TABLE labor_shifts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON labor_shifts;
CREATE POLICY tenant_isolation ON labor_shifts
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE labor_time_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE labor_time_entries FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON labor_time_entries;
CREATE POLICY tenant_isolation ON labor_time_entries
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON labor_shifts TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON labor_shifts TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON labor_time_entries TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON labor_time_entries TO app_owner;
