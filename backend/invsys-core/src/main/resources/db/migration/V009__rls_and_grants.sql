-- Enable RLS on all tenant-scoped tables
DO $$
DECLARE
    t TEXT;
    tables TEXT[] := ARRAY[
        'tenant_settings', 'users', 'roles', 'user_roles', 'refresh_tokens', 'invitations',
        'locations', 'products', 'product_variants', 'lots',
        'inventory_ledger', 'allocations', 'inventory_levels', 'cycle_counts', 'cycle_count_lines',
        'suppliers', 'purchase_orders', 'purchase_order_lines',
        'customers', 'sales_orders', 'sales_order_lines', 'shipments', 'shipment_lines',
        'invoices', 'invoice_lines', 'stripe_accounts', 'payment_intents', 'payments',
        'document_sequences', 'idempotency_keys', 'webhook_events', 'outbox_events',
        'external_references', 'audit_log'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = current_setting(''app.current_tenant'', true)::uuid) WITH CHECK (tenant_id = current_setting(''app.current_tenant'', true)::uuid)',
            t
        );
    END LOOP;
END $$;

-- Tenants: id is the tenant identifier
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenants;
CREATE POLICY tenant_isolation ON tenants
    USING (id = current_setting('app.current_tenant', true)::uuid)
    WITH CHECK (id = current_setting('app.current_tenant', true)::uuid);

-- Webhooks may arrive before tenant is resolved
DROP POLICY IF EXISTS webhook_insert ON webhook_events;
CREATE POLICY webhook_insert ON webhook_events
    FOR INSERT WITH CHECK (true);
DROP POLICY IF EXISTS webhook_tenant_read ON webhook_events;
CREATE POLICY webhook_tenant_read ON webhook_events
    FOR SELECT USING (
        tenant_id IS NULL OR tenant_id = current_setting('app.current_tenant', true)::uuid
    );

-- Grants to app_user (runtime role)
GRANT USAGE ON SCHEMA public TO app_user;

GRANT SELECT, INSERT, UPDATE, DELETE ON
    tenants, tenant_settings, users, roles, user_roles, refresh_tokens, invitations,
    locations, products, product_variants, lots,
    allocations, inventory_levels, cycle_counts, cycle_count_lines,
    suppliers, purchase_orders, purchase_order_lines,
    customers, sales_orders, sales_order_lines, shipments, shipment_lines,
    invoices, invoice_lines, stripe_accounts, payment_intents, payments,
    document_sequences, idempotency_keys, webhook_events, outbox_events,
    external_references, audit_log
TO app_user;

-- Ledger is append-only: INSERT + SELECT only
GRANT SELECT, INSERT ON inventory_ledger TO app_user;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_user;
