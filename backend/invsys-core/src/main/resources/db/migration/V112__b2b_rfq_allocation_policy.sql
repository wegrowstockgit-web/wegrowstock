-- B2B quote-to-order (RFQ) + ship-complete vs split-shipment allocation policy.
ALTER TABLE sales_orders DROP CONSTRAINT IF EXISTS sales_orders_status_check;
ALTER TABLE sales_orders ALTER COLUMN status TYPE VARCHAR(40);
ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_status_check
    CHECK (status IN (
        'DRAFT', 'DRAFT_QUOTE', 'PENDING_REP_APPROVAL', 'QUOTE_READY', 'QUOTE_ACCEPTED',
        'UNALLOCATED', 'CONFIRMED', 'NEEDS_REVIEW', 'PARTIALLY_ALLOCATED', 'ALLOCATED',
        'BACKORDERED', 'PICKING', 'PARTIALLY_SHIPPED', 'SHIPPED',
        'HOLD', 'CREDIT_HOLD', 'CLOSED', 'CANCELLED'
    ));

ALTER TABLE sales_orders
    ADD COLUMN IF NOT EXISTS allocation_policy VARCHAR(20) NOT NULL DEFAULT 'ALLOW_PARTIAL',
    ADD COLUMN IF NOT EXISTS quote_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS manual_discount_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS quote_notes TEXT;

ALTER TABLE sales_orders DROP CONSTRAINT IF EXISTS sales_orders_allocation_policy_check;
ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_allocation_policy_check
    CHECK (allocation_policy IN ('SHIP_COMPLETE', 'ALLOW_PARTIAL'));

ALTER TABLE sales_order_lines
    ADD COLUMN IF NOT EXISTS qty_backordered NUMERIC(19,4) NOT NULL DEFAULT 0;
