-- V073: Allow DISPATCHED status on license plates for outbound pallet shipping

ALTER TABLE license_plates
    DROP CONSTRAINT IF EXISTS license_plates_status_check;

ALTER TABLE license_plates
    ADD CONSTRAINT license_plates_status_check
        CHECK (status IN ('OPEN', 'IN_TRANSIT', 'CLOSED', 'DISPATCHED'));

COMMENT ON COLUMN license_plates.status IS 'OPEN | IN_TRANSIT | CLOSED | DISPATCHED (shipped outbound)';
