-- V080: Enterprise ProductVariant trade / handling / lifecycle + location putaway constraints

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS hs_tariff_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS country_of_origin VARCHAR(2),
    ADD COLUMN IF NOT EXISTS is_hazmat BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pallet_tie INTEGER,
    ADD COLUMN IF NOT EXISTS pallet_high INTEGER,
    ADD COLUMN IF NOT EXISTS storage_temp_zone VARCHAR(32) NOT NULL DEFAULT 'AMBIENT',
    ADD COLUMN IF NOT EXISTS is_fragile BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS abc_classification VARCHAR(1) NOT NULL DEFAULT 'C',
    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS product_variants_storage_temp_zone_chk;
ALTER TABLE product_variants
    ADD CONSTRAINT product_variants_storage_temp_zone_chk
        CHECK (storage_temp_zone IN ('AMBIENT', 'REFRIGERATED', 'FROZEN'));

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS product_variants_abc_classification_chk;
ALTER TABLE product_variants
    ADD CONSTRAINT product_variants_abc_classification_chk
        CHECK (abc_classification IN ('A', 'B', 'C'));

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS product_variants_lifecycle_status_chk;
ALTER TABLE product_variants
    ADD CONSTRAINT product_variants_lifecycle_status_chk
        CHECK (lifecycle_status IN ('PRE_RELEASE', 'ACTIVE', 'PHASE_OUT', 'DISCONTINUED'));

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS product_variants_pallet_tie_chk;
ALTER TABLE product_variants
    ADD CONSTRAINT product_variants_pallet_tie_chk
        CHECK (pallet_tie IS NULL OR pallet_tie > 0);

ALTER TABLE product_variants
    DROP CONSTRAINT IF EXISTS product_variants_pallet_high_chk;
ALTER TABLE product_variants
    ADD CONSTRAINT product_variants_pallet_high_chk
        CHECK (pallet_high IS NULL OR pallet_high > 0);

-- Bin constraints used by putaway tiered validation
ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS storage_temp_zone VARCHAR(32) NOT NULL DEFAULT 'AMBIENT',
    ADD COLUMN IF NOT EXISTS allows_hazmat BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS max_pallet_positions INTEGER;

ALTER TABLE locations
    DROP CONSTRAINT IF EXISTS locations_storage_temp_zone_chk;
ALTER TABLE locations
    ADD CONSTRAINT locations_storage_temp_zone_chk
        CHECK (storage_temp_zone IN ('AMBIENT', 'REFRIGERATED', 'FROZEN'));

ALTER TABLE locations
    DROP CONSTRAINT IF EXISTS locations_max_pallet_positions_chk;
ALTER TABLE locations
    ADD CONSTRAINT locations_max_pallet_positions_chk
        CHECK (max_pallet_positions IS NULL OR max_pallet_positions > 0);

CREATE INDEX IF NOT EXISTS idx_product_variants_lifecycle
    ON product_variants (tenant_id, lifecycle_status);

CREATE INDEX IF NOT EXISTS idx_product_variants_abc
    ON product_variants (tenant_id, abc_classification);

-- Dangerous-goods documentation flag set during cartonization / label buy
ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS requires_dg_documentation BOOLEAN NOT NULL DEFAULT FALSE;
