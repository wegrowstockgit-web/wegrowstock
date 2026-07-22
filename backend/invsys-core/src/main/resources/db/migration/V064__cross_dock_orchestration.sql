-- Cross-dock orchestration: allow BACKORDERED sales order status.
-- Shipping staging bins (Z-SHIP / S-01) are provisioned via ops/demo_seed.sql
-- (Flyway migrator cannot INSERT into FORCE RLS tables without BYPASSRLS).

ALTER TABLE sales_orders DROP CONSTRAINT IF EXISTS sales_orders_status_check;
ALTER TABLE sales_orders ADD CONSTRAINT sales_orders_status_check
    CHECK (status IN (
        'DRAFT', 'CONFIRMED', 'BACKORDERED', 'ALLOCATED',
        'PARTIALLY_SHIPPED', 'SHIPPED', 'CLOSED', 'CANCELLED'
    ));
