-- Composite indexes for tenant-scoped offset pagination + sort on operational tables.

CREATE INDEX IF NOT EXISTS idx_po_tenant_created
    ON purchase_orders (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_po_tenant_number
    ON purchase_orders (tenant_id, number);

CREATE INDEX IF NOT EXISTS idx_suppliers_tenant_name
    ON suppliers (tenant_id, name);

CREATE INDEX IF NOT EXISTS idx_so_tenant_created
    ON sales_orders (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_so_tenant_number
    ON sales_orders (tenant_id, number);

CREATE INDEX IF NOT EXISTS idx_customers_tenant_name
    ON customers (tenant_id, name);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant_created
    ON invoices (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_invoices_tenant_number
    ON invoices (tenant_id, number);

CREATE INDEX IF NOT EXISTS idx_products_tenant_name
    ON products (tenant_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_products_tenant_sku
    ON products (tenant_id, sku_root)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_mfg_tenant_created
    ON production_orders (tenant_id, created_at DESC);
