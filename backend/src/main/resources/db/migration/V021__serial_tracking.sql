-- Track 10: Unique serial number tracking

ALTER TABLE product_variants
    ADD COLUMN track_serials BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE serial_numbers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id    UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    serial_number VARCHAR(100) NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK'
        CHECK (status IN ('IN_STOCK', 'SHIPPED', 'SCRAPPED', 'RMA_RETURNED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, variant_id, serial_number)
);

CREATE INDEX idx_serial_numbers_lookup ON serial_numbers(tenant_id, serial_number);
CREATE INDEX idx_serial_numbers_variant ON serial_numbers(tenant_id, variant_id, status);

CREATE TRIGGER serial_numbers_updated_at BEFORE UPDATE ON serial_numbers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE inventory_ledger
    ADD COLUMN serial_number_id UUID REFERENCES serial_numbers(id);

CREATE INDEX idx_inventory_ledger_serial ON inventory_ledger(serial_number_id)
    WHERE serial_number_id IS NOT NULL;

ALTER TABLE allocations
    ADD COLUMN serial_number_id UUID REFERENCES serial_numbers(id);

CREATE INDEX idx_allocations_serial ON allocations(serial_number_id)
    WHERE serial_number_id IS NOT NULL;

-- Prevent assigning SHIPPED/SCRAPPED serials to outbound movements
CREATE OR REPLACE FUNCTION validate_serial_outbound()
RETURNS TRIGGER AS $$
DECLARE
    serial_status VARCHAR(30);
BEGIN
    IF NEW.serial_number_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NEW.movement_type IN ('SHIP', 'ASSEMBLY_OUT', 'TRANSFER_OUT') THEN
        SELECT status INTO serial_status
        FROM serial_numbers
        WHERE id = NEW.serial_number_id;
        IF serial_status IS NULL OR serial_status NOT IN ('IN_STOCK', 'RMA_RETURNED') THEN
            RAISE EXCEPTION 'Serial number % is not available for outbound movement', NEW.serial_number_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER inventory_ledger_serial_outbound
    BEFORE INSERT ON inventory_ledger
    FOR EACH ROW EXECUTE FUNCTION validate_serial_outbound();

ALTER TABLE serial_numbers ENABLE ROW LEVEL SECURITY;
ALTER TABLE serial_numbers FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON serial_numbers;
CREATE POLICY tenant_isolation ON serial_numbers
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON serial_numbers TO app_user;
