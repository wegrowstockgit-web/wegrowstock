-- Track 14: Core schema enrichment (competitor parity)
-- Note: blueprint references V019; V019 is already used — this is V024.

ALTER TABLE product_variants
    ADD COLUMN IF NOT EXISTS weight NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS weight_unit VARCHAR(10) NOT NULL DEFAULT 'kg',
    ADD COLUMN IF NOT EXISTS length NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS width NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS height NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS dim_unit VARCHAR(10) NOT NULL DEFAULT 'cm',
    ADD COLUMN IF NOT EXISTS default_supplier_id UUID REFERENCES suppliers(id),
    ADD COLUMN IF NOT EXISTS supplier_lead_time_days INT NOT NULL DEFAULT 0;

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS destination_location_id UUID REFERENCES locations(id),
    ADD COLUMN IF NOT EXISTS freight_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS duties_amount NUMERIC(19,4) NOT NULL DEFAULT 0;

ALTER TABLE sales_orders
    ADD COLUMN IF NOT EXISTS source_location_id UUID REFERENCES locations(id),
    ADD COLUMN IF NOT EXISTS customer_po_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS requested_ship_date TIMESTAMPTZ;

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS tax_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS default_currency CHAR(3);

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS tax_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS default_currency CHAR(3);

ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS total_weight NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS postage_amount NUMERIC(19,4);
