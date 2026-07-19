-- Channel inbound orders may land in NEEDS_REVIEW when SKUs/soft-kits cannot be resolved.
ALTER TABLE sales_orders DROP CONSTRAINT IF EXISTS sales_orders_status_check;
ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_status_check
    CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'NEEDS_REVIEW', 'BACKORDERED', 'ALLOCATED',
        'PARTIALLY_SHIPPED', 'SHIPPED', 'CLOSED', 'CANCELLED'
    ));
