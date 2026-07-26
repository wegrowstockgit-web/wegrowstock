-- V102: Dock door scheduling / yard management (YMS).

CREATE TABLE IF NOT EXISTS dock_appointments (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    warehouse_id         UUID NOT NULL REFERENCES locations(id),
    dock_door_number     INT NOT NULL,
    purchase_order_id    UUID REFERENCES purchase_orders(id),
    carrier_name         VARCHAR(100),
    driver_name          VARCHAR(100),
    truck_license_plate  VARCHAR(50),
    appointment_start    TIMESTAMPTZ NOT NULL,
    appointment_end      TIMESTAMPTZ NOT NULL,
    status               VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT dock_appointments_time_chk CHECK (appointment_end > appointment_start)
);

CREATE INDEX IF NOT EXISTS idx_dock_appointments_slot
    ON dock_appointments (tenant_id, warehouse_id, dock_door_number, appointment_start);

CREATE INDEX IF NOT EXISTS idx_dock_appointments_status
    ON dock_appointments (tenant_id, status, appointment_start);

ALTER TABLE dock_appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE dock_appointments FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON dock_appointments;
CREATE POLICY tenant_isolation ON dock_appointments
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON dock_appointments TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON dock_appointments TO app_owner;
