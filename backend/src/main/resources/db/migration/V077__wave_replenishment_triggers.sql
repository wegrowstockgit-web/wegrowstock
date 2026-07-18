-- V077: Predictive min/max replenishment wave triggers (forward-stage pick faces)

CREATE TABLE wave_replenishment_triggers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id          UUID NOT NULL REFERENCES product_variants(id),
    location_id         UUID NOT NULL REFERENCES locations(id),
    current_bin_qty     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    projected_demand    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    min_threshold       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    target_qty          NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_wave_replen_triggers_active
    ON wave_replenishment_triggers (tenant_id, status, location_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_wave_replen_triggers_open
    ON wave_replenishment_triggers (tenant_id, variant_id, location_id)
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE TRIGGER wave_replenishment_triggers_updated_at
    BEFORE UPDATE ON wave_replenishment_triggers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE wave_replenishment_triggers ENABLE ROW LEVEL SECURITY;
ALTER TABLE wave_replenishment_triggers FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON wave_replenishment_triggers
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON wave_replenishment_triggers TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON wave_replenishment_triggers TO app_owner;

COMMENT ON TABLE wave_replenishment_triggers IS
    'Predictive 48h demand vs pick-face qty; ACTIVE rows feed task interleaving';
