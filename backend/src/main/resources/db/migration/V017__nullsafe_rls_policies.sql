-- The GUC app.current_tenant can be '' (empty string) at session level once any
-- transaction-local set_config has run on the connection. current_setting(...)::uuid
-- then raises 22P02 instead of failing closed. Recreate every tenant policy with
-- nullif(..., '') so an unset/empty GUC yields NULL (no rows) instead of an error.

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
        'external_references', 'audit_log',
        'integration_credentials', 'integration_sync_logs', 'integration_rate_buckets',
        'boms', 'bom_lines', 'production_orders',
        'returns', 'return_lines',
        'account_mappings', 'channel_integrations',
        'customer_user_mappings', 'customer_price_tiers'
    ];
BEGIN
    FOREACH t IN ARRAY tables LOOP
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid) WITH CHECK (tenant_id = nullif(current_setting(''app.current_tenant'', true), '''')::uuid)',
            t
        );
    END LOOP;
END $$;

DROP POLICY IF EXISTS tenant_isolation ON tenants;
CREATE POLICY tenant_isolation ON tenants
    USING (id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (id = nullif(current_setting('app.current_tenant', true), '')::uuid);

DROP POLICY IF EXISTS webhook_tenant_read ON webhook_events;
CREATE POLICY webhook_tenant_read ON webhook_events
    FOR SELECT USING (
        tenant_id IS NULL OR tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid
    );
