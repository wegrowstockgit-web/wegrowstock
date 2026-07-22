-- V078: Enterprise warehouse facility specs + customer/supplier/user master data

-- ---------------------------------------------------------------------------
-- Warehouse (locations.type = WAREHOUSE) facility attributes
-- ---------------------------------------------------------------------------
ALTER TABLE locations
    ADD COLUMN IF NOT EXISTS logistics_address JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS gross_square_footage NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS office_area_square_footage NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS clear_height_feet NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS total_dock_doors INTEGER,
    ADD COLUMN IF NOT EXISTS weight_capacity_limit NUMERIC(19, 4);

COMMENT ON COLUMN locations.logistics_address IS 'Street/city/state/postal/country for facility shipping logistics';
COMMENT ON COLUMN locations.clear_height_feet IS 'Usable vertical clear height (floor to lowest obstruction)';
COMMENT ON COLUMN locations.weight_capacity_limit IS 'Floor / facility weight capacity limit (tenant units)';

-- ---------------------------------------------------------------------------
-- Customers — ERP commercial fields (tax_id / default_currency already exist)
-- ---------------------------------------------------------------------------
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS payment_terms VARCHAR(32),
    ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS currency_preference CHAR(3),
    ADD COLUMN IF NOT EXISTS customer_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- Backfill currency preference from legacy default_currency when present
UPDATE customers
SET currency_preference = default_currency
WHERE currency_preference IS NULL AND default_currency IS NOT NULL;

-- Enum enforcement is application-level (CreateCustomerRequest normalizePaymentTerms).
-- Avoid CHECK constraints that break on legacy free-text demo rows.

-- ---------------------------------------------------------------------------
-- Suppliers — vendor master expansions
-- ---------------------------------------------------------------------------
ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS business_registration VARCHAR(128),
    ADD COLUMN IF NOT EXISTS bank_account_iban VARCHAR(64),
    ADD COLUMN IF NOT EXISTS bank_routing_number VARCHAR(64),
    ADD COLUMN IF NOT EXISTS default_lead_time_days INTEGER,
    ADD COLUMN IF NOT EXISTS minimum_order_quantity_value NUMERIC(19, 4),
    ADD COLUMN IF NOT EXISTS supplier_rating NUMERIC(5, 2);

-- Normalize free-text payment terms into enum-ish values where possible
UPDATE suppliers
SET payment_terms = 'NET30'
WHERE payment_terms IS NOT NULL
  AND upper(replace(payment_terms, ' ', '')) IN ('NET30', 'NET_30', 'N30');

UPDATE suppliers
SET payment_terms = 'NET60'
WHERE payment_terms IS NOT NULL
  AND upper(replace(payment_terms, ' ', '')) IN ('NET60', 'NET_60', 'N60');

UPDATE suppliers
SET payment_terms = 'DUE_ON_RECEIPT'
WHERE payment_terms IS NOT NULL
  AND upper(replace(replace(payment_terms, ' ', ''), '_', '')) IN ('DUEONRECEIPT', 'COD', 'DUE');

-- Leave remaining free-text terms as-is; new writes are normalized in the API.
ALTER TABLE suppliers
    DROP CONSTRAINT IF EXISTS chk_suppliers_rating;
ALTER TABLE suppliers
    ADD CONSTRAINT chk_suppliers_rating
    CHECK (supplier_rating IS NULL OR (supplier_rating >= 0 AND supplier_rating <= 5));

-- ---------------------------------------------------------------------------
-- Users — employee / floor-operator preferences
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS department VARCHAR(128),
    ADD COLUMN IF NOT EXISTS timezone_preference VARCHAR(64),
    ADD COLUMN IF NOT EXISTS locale_language VARCHAR(16),
    ADD COLUMN IF NOT EXISTS assigned_warehouse_id UUID,
    ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS shift_schedule VARCHAR(32);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS fk_users_assigned_warehouse;
ALTER TABLE users
    ADD CONSTRAINT fk_users_assigned_warehouse
    FOREIGN KEY (assigned_warehouse_id) REFERENCES locations (id);

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS chk_users_shift_schedule;
ALTER TABLE users
    ADD CONSTRAINT chk_users_shift_schedule
    CHECK (shift_schedule IS NULL OR shift_schedule IN ('DAY', 'SWING', 'NIGHT', 'FLEX'));
