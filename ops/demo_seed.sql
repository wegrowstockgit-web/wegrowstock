-- =============================================================================
-- InventorySystem Demo Seed Data
-- LOCAL / DOCKER ONLY. Never apply this file to shared or production databases.
-- Populates ALL tables with realistic test data for four tenants.
--
-- Usage (after Flyway migrations have run):
--   docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed.sql
--   docker compose exec db psql -U app_owner -d invsys -f /seed/demo_seed_tenants_extra.sql
--
-- Demo credentials (password for all users): password123
-- Tenants (commercial tiers for control-plane testing):
--   Demo Corp (ENTERPRISE): CORE + B2B_SHOWROOM + FINTECH + AI_COPILOT + RETAIL_POS + MESH_NETWORK
--   Owner users on BASIC / INTERMEDIATE do not receive Mesh or B2B (tier-gated).
--   Owner users on ENTERPRISE (owner@demo.test and any other ENTERPRISE owner) do.
--   Acme Wholesale (BASIC): CORE
--   Northwind Logistics (INTERMEDIATE): CORE + SHOPIFY + ADVANCED_FULFILLMENT
--   Pacific Parts Co (BASIC): CORE — seeded via demo_seed_tenants_extra.sql
-- Super Admin (platform_admins): owner@demo.test / password123 via admin.invsys.com
-- WMS Demo Owner (users): owner@demo.test / password123 via app.invsys.com (tenant user, not platform admin)
-- Picker LBAC (Demo Corp): WH-01 Main Warehouse only (cannot select WH-02)
-- Role coverage (every tenant): OWNER, ADMIN, WAREHOUSE_MANAGER, PICKER, VIEWER,
--   B2B_CUSTOMER, RETAIL_CASHIER, RETAIL_MANAGER, SUPPLIER
-- POS-only (cannot WMS): cashier@*.test, retailmgr@*.test  — register at :3003
--   Only Demo Corp has RETAIL_POS; other tenants login to assert posEnabled=false
-- Vendor portal: supplier@*.test (mapped to the primary supplier)
-- POS manager override PIN: warehouse manager 1234, retail manager 5678
-- =============================================================================

BEGIN;

-- Historical ledger rows in this seed use created_at up to ~60 days ago.
SELECT ensure_monthly_partitions(
    'inventory_ledger'::regclass,
    (date_trunc('month', NOW()) - INTERVAL '12 months')::date,
    19
);
SELECT ensure_monthly_partitions(
    'audit_log'::regclass,
    (date_trunc('month', NOW()) - INTERVAL '12 months')::date,
    19
);

-- bcrypt hash of "password123" (BCryptPasswordEncoder strength 10)
-- $2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu

-- =========================================================================
-- TENANT A: Demo Corp (a0000000-0000-4000-8000-000000000001)
-- =========================================================================
SELECT set_config('app.current_tenant', 'a0000000-0000-4000-8000-000000000001', true);

INSERT INTO tenants (id, name, slug, status) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'Demo Corp', 'demo-corp', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenant_settings (id, tenant_id, settings) VALUES
    ('a0000000-0000-4000-8000-000000000010', 'a0000000-0000-4000-8000-000000000001',
     '{"company_name":"Demo Corp","currency":"USD","timezone":"America/New_York","allow_negative_inventory":false,"barcode_prefix":"","barcode_suffix":"","default_reorder_point":10,"default_reorder_qty":50,"invoice_number_format":"INV-{YYYY}-{seq:5}","platform_fee_percent":0.4,"payment_terms_days":30,"smtp_host":"mailpit","smtp_port":1025,"smtp_from":"noreply@demo.test","smtp_auth":false}'::jsonb)
ON CONFLICT (tenant_id) DO NOTHING;

UPDATE tenant_settings
SET settings = settings || jsonb_build_object(
    'smtp_host', 'mailpit',
    'smtp_port', 1025,
    'smtp_from', 'noreply@demo.test',
    'smtp_auth', false
)
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001';

INSERT INTO currency_rates (id, from_currency, to_currency, rate, as_of) VALUES
    ('a0000000-0000-4000-8000-00000000c001', 'USD', 'MXN', 18.500000, NOW())
ON CONFLICT (from_currency, to_currency) DO UPDATE SET
    rate = EXCLUDED.rate,
    as_of = EXCLUDED.as_of;

INSERT INTO tenant_subscriptions (tenant_id, tier, enabled_modules, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'ENTERPRISE',
     '["CORE","B2B_SHOWROOM","FINTECH","AI_COPILOT","RETAIL_POS","MESH_NETWORK"]'::jsonb, NOW())
ON CONFLICT (tenant_id) DO UPDATE SET
    tier = EXCLUDED.tier,
    enabled_modules = EXCLUDED.enabled_modules,
    updated_at = NOW();

-- Platform Super Admin (control plane only — not a tenant user flag)
INSERT INTO platform_admins (id, email, password_hash, active) VALUES
    ('e0000000-0000-4000-8000-000000000001', 'owner@demo.test',
     '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', true)
ON CONFLICT (email) DO UPDATE SET
    password_hash = EXCLUDED.password_hash,
    active = TRUE;

INSERT INTO roles (id, tenant_id, code, network_access_level) VALUES
    ('a0000000-0000-4000-8000-000000000101', 'a0000000-0000-4000-8000-000000000001', 'OWNER', 'MFA_OUTSIDE_NETWORK'),
    ('a0000000-0000-4000-8000-000000000102', 'a0000000-0000-4000-8000-000000000001', 'ADMIN', 'MFA_OUTSIDE_NETWORK'),
    ('a0000000-0000-4000-8000-000000000103', 'a0000000-0000-4000-8000-000000000001', 'WAREHOUSE_MANAGER', 'MFA_OUTSIDE_NETWORK'),
    ('a0000000-0000-4000-8000-000000000104', 'a0000000-0000-4000-8000-000000000001', 'PICKER', 'STRICT_INTERNAL'),
    ('a0000000-0000-4000-8000-000000000105', 'a0000000-0000-4000-8000-000000000001', 'VIEWER', 'MFA_OUTSIDE_NETWORK'),
    ('a0000000-0000-4000-8000-000000000106', 'a0000000-0000-4000-8000-000000000001', 'B2B_CUSTOMER', 'ROAMING'),
    ('a0000000-0000-4000-8000-000000000107', 'a0000000-0000-4000-8000-000000000001', 'RETAIL_CASHIER', 'STRICT_INTERNAL'),
    ('a0000000-0000-4000-8000-000000000108', 'a0000000-0000-4000-8000-000000000001', 'RETAIL_MANAGER', 'MFA_OUTSIDE_NETWORK'),
    ('a0000000-0000-4000-8000-000000000109', 'a0000000-0000-4000-8000-000000000001', 'SUPPLIER', 'ROAMING')
ON CONFLICT (tenant_id, code) DO UPDATE SET
    network_access_level = EXCLUDED.network_access_level;

INSERT INTO users (id, tenant_id, email, password_hash, display_name, status) VALUES
    ('a0000000-0000-4000-8000-000000000201', 'a0000000-0000-4000-8000-000000000001', 'owner@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Demo Owner', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000202', 'a0000000-0000-4000-8000-000000000001', 'admin@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Demo Admin', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000203', 'a0000000-0000-4000-8000-000000000001', 'manager@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Warehouse Manager', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000204', 'a0000000-0000-4000-8000-000000000001', 'picker@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Floor Picker', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000205', 'a0000000-0000-4000-8000-000000000001', 'viewer@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Read Only User', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000206', 'a0000000-0000-4000-8000-000000000001', 'b2b@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'B2B Buyer', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000207', 'a0000000-0000-4000-8000-000000000001', 'cashier@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Retail Cashier', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000208', 'a0000000-0000-4000-8000-000000000001', 'retailmgr@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Retail Manager', 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000000209', 'a0000000-0000-4000-8000-000000000001', 'supplier@demo.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Global Parts Vendor', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (id, tenant_id, user_id, role_id) VALUES
    ('a0000000-0000-4000-8000-000000000301', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000201', 'a0000000-0000-4000-8000-000000000101'),
    ('a0000000-0000-4000-8000-000000000302', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000202', 'a0000000-0000-4000-8000-000000000102'),
    ('a0000000-0000-4000-8000-000000000303', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000203', 'a0000000-0000-4000-8000-000000000103'),
    ('a0000000-0000-4000-8000-000000000304', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000204', 'a0000000-0000-4000-8000-000000000104'),
    ('a0000000-0000-4000-8000-000000000305', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000205', 'a0000000-0000-4000-8000-000000000105'),
    ('a0000000-0000-4000-8000-000000000306', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000206', 'a0000000-0000-4000-8000-000000000106')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Look up retail/supplier roles by code: V118 may have created RETAIL_* with random UUIDs.
INSERT INTO user_roles (id, tenant_id, user_id, role_id)
SELECT v.id, u.tenant_id, u.id, r.id
FROM (VALUES
    ('a0000000-0000-4000-8000-000000000307'::uuid, 'a0000000-0000-4000-8000-000000000207'::uuid, 'RETAIL_CASHIER'),
    ('a0000000-0000-4000-8000-000000000308'::uuid, 'a0000000-0000-4000-8000-000000000208'::uuid, 'RETAIL_MANAGER'),
    ('a0000000-0000-4000-8000-000000000309'::uuid, 'a0000000-0000-4000-8000-000000000209'::uuid, 'SUPPLIER')
) AS v(id, user_id, role_code)
JOIN users u ON u.id = v.user_id
JOIN roles r ON r.tenant_id = u.tenant_id AND r.code = v.role_code
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO role_permissions (tenant_id, role_id, permission_key, granted)
SELECT r.tenant_id, r.id, p.permission_key,
       CASE
           WHEN r.code IN ('OWNER', 'ADMIN') THEN TRUE
           WHEN r.code IN ('WAREHOUSE_MANAGER', 'RETAIL_MANAGER') THEN TRUE
           WHEN r.code = 'RETAIL_CASHIER' THEN p.permission_key = 'pos.operate'
           ELSE FALSE
       END
FROM roles r
CROSS JOIN (VALUES ('pos.operate'), ('pos.supervise')) AS p(permission_key)
WHERE r.tenant_id = 'a0000000-0000-4000-8000-000000000001'
  AND r.code IN (
      'OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER',
      'RETAIL_CASHIER', 'RETAIL_MANAGER'
  )
ON CONFLICT (tenant_id, role_id, permission_key) DO UPDATE
    SET granted = EXCLUDED.granted,
        updated_at = NOW();

-- POS manager override PINs (unique per tenant). Floor shift PIN stays browser-local.
UPDATE users
SET terminal_pin_hash = encode(digest(tenant_id::text || ':1234', 'sha256'), 'hex')
WHERE id = 'a0000000-0000-4000-8000-000000000203';
UPDATE users
SET terminal_pin_hash = encode(digest(tenant_id::text || ':5678', 'sha256'), 'hex')
WHERE id = 'a0000000-0000-4000-8000-000000000208';

INSERT INTO refresh_tokens (id, tenant_id, user_id, token_hash, expires_at) VALUES
    ('a0000000-0000-4000-8000-000000000401', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000201',
     'seed_refresh_token_owner_hash', NOW() + INTERVAL '7 days')
ON CONFLICT (token_hash) DO NOTHING;

INSERT INTO invitations (id, tenant_id, email, role_id, token_hash, invited_by, expires_at) VALUES
    ('a0000000-0000-4000-8000-000000000501', 'a0000000-0000-4000-8000-000000000001', 'newpicker@demo.test',
     'a0000000-0000-4000-8000-000000000104', 'seed_invite_token_hash_picker',
     'a0000000-0000-4000-8000-000000000201', NOW() + INTERVAL '7 days')
ON CONFLICT (token_hash) DO NOTHING;

INSERT INTO locations (id, tenant_id, parent_location_id, type, code, name, path) VALUES
    ('a0000000-0000-4000-8000-000000000601', 'a0000000-0000-4000-8000-000000000001', NULL, 'WAREHOUSE', 'WH-01', 'Main Warehouse', 'WH-01'),
    ('a0000000-0000-4000-8000-000000000602', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000601', 'ZONE', 'Z-A', 'Zone A', 'WH-01/Z-A'),
    ('a0000000-0000-4000-8000-000000000603', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000602', 'AISLE', 'A-1', 'Aisle 1', 'WH-01/Z-A/A-1'),
    ('a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000603', 'BIN', 'B-01', 'Bin 01', 'WH-01/Z-A/A-1/B-01'),
    ('a0000000-0000-4000-8000-000000000605', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000603', 'BIN', 'B-02', 'Bin 02', 'WH-01/Z-A/A-1/B-02'),
    ('a0000000-0000-4000-8000-000000000611', 'a0000000-0000-4000-8000-000000000001', NULL, 'WAREHOUSE', 'WH-02', 'Overflow Warehouse', 'WH-02'),
    ('a0000000-0000-4000-8000-000000000612', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000611', 'ZONE', 'Z-B', 'Zone B', 'WH-02/Z-B'),
    ('a0000000-0000-4000-8000-000000000613', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000612', 'BIN', 'B-10', 'Bin 10', 'WH-02/Z-B/B-10'),
    ('a0000000-0000-4000-8000-000000000621', 'a0000000-0000-4000-8000-000000000001', NULL, 'VEHICLE', 'VAN-01', 'Service Van 01', 'VAN-01'),
    ('a0000000-0000-4000-8000-000000000630', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000601', 'ZONE', 'Z-SHIP', 'Shipping Staging Zone', 'WH-01/Z-SHIP'),
    ('a0000000-0000-4000-8000-000000000631', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000630', 'BIN', 'S-01', 'Staging Lane 01', 'WH-01/Z-SHIP/S-01')
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Active technician truck for Demo Owner (Technician Truck page)
INSERT INTO vehicle_assignments (id, tenant_id, location_id, technician_user_id, assigned_at)
SELECT 'a0000000-0000-4000-8000-000000000371',
       'a0000000-0000-4000-8000-000000000001',
       'a0000000-0000-4000-8000-000000000621',
       'a0000000-0000-4000-8000-000000000201',
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM vehicle_assignments
    WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001'
      AND technician_user_id = 'a0000000-0000-4000-8000-000000000201'
      AND returned_at IS NULL
);

-- LBAC: picker + manager + viewer scoped to Main Warehouse (WH-01) only
INSERT INTO user_warehouses (id, tenant_id, user_id, location_id) VALUES
    ('a0000000-0000-4000-8000-000000000351', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000204', 'a0000000-0000-4000-8000-000000000601'),
    ('a0000000-0000-4000-8000-000000000352', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000203', 'a0000000-0000-4000-8000-000000000601'),
    ('a0000000-0000-4000-8000-000000000353', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000205', 'a0000000-0000-4000-8000-000000000601'),
    ('a0000000-0000-4000-8000-000000000354', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000207', 'a0000000-0000-4000-8000-000000000601'),
    ('a0000000-0000-4000-8000-000000000355', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000208', 'a0000000-0000-4000-8000-000000000601')
ON CONFLICT (user_id, location_id) DO NOTHING;

INSERT INTO products (id, tenant_id, sku_root, name, description) VALUES
    ('a0000000-0000-4000-8000-000000000701', 'a0000000-0000-4000-8000-000000000001', 'WIDGET', 'Industrial Widget', 'Standard widget'),
    ('a0000000-0000-4000-8000-000000000702', 'a0000000-0000-4000-8000-000000000001', 'GADGET', 'Smart Gadget', 'Connected gadget'),
    ('a0000000-0000-4000-8000-000000000703', 'a0000000-0000-4000-8000-000000000001', 'BOLT', 'Hex Bolt Pack', 'M8 bolts'),
    ('a0000000-0000-4000-8000-000000000704', 'a0000000-0000-4000-8000-000000000001', 'TAPE', 'Packing Tape', 'Heavy duty tape'),
    ('a0000000-0000-4000-8000-000000000705', 'a0000000-0000-4000-8000-000000000001', 'BOX', 'Shipping Box', 'Corrugated box')
ON CONFLICT (tenant_id, sku_root) DO NOTHING;

INSERT INTO product_variants (id, tenant_id, product_id, sku, barcode, attributes, price, currency, reorder_point, reorder_qty) VALUES
    ('a0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000701', 'WIDGET-S', '8901000000001', '{"size":"S","color":"blue"}', 12.50, 'USD', 20, 100),
    ('a0000000-0000-4000-8000-000000000802', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000701', 'WIDGET-L', '8901000000002', '{"size":"L","color":"blue"}', 15.00, 'USD', 15, 75),
    ('a0000000-0000-4000-8000-000000000803', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000702', 'GADGET-BLK', '8901000000003', '{"color":"black"}', 49.99, 'USD', 10, 50),
    ('a0000000-0000-4000-8000-000000000804', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000702', 'GADGET-WHT', '8901000000004', '{"color":"white"}', 49.99, 'USD', 10, 50),
    ('a0000000-0000-4000-8000-000000000805', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000703', 'BOLT-M8-50', '8901000000005', '{"length":"50mm"}', 8.99, 'USD', 30, 200),
    ('a0000000-0000-4000-8000-000000000806', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000704', 'TAPE-2IN', '8901000000006', '{"width":"2in"}', 4.50, 'USD', 25, 100),
    ('a0000000-0000-4000-8000-000000000807', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000705', 'BOX-MED', '8901000000007', '{"size":"medium"}', 2.25, 'USD', 50, 500),
    ('a0000000-0000-4000-8000-000000000808', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000705', 'BOX-LRG', '8901000000008', '{"size":"large"}', 3.50, 'USD', 40, 400)
ON CONFLICT (tenant_id, sku) DO NOTHING;

-- Variant dims (inches / lb) for cartonization demos
UPDATE product_variants SET
    length = 6, width = 4, height = 3, dim_unit = 'in',
    weight = 0.75, weight_unit = 'lb'
WHERE id = 'a0000000-0000-4000-8000-000000000801';
UPDATE product_variants SET
    length = 8, width = 5, height = 4, dim_unit = 'in',
    weight = 1.10, weight_unit = 'lb'
WHERE id = 'a0000000-0000-4000-8000-000000000802';
UPDATE product_variants SET
    length = 10, width = 6, height = 4, dim_unit = 'in',
    weight = 2.40, weight_unit = 'lb'
WHERE id IN ('a0000000-0000-4000-8000-000000000803', 'a0000000-0000-4000-8000-000000000804');
UPDATE product_variants SET
    length = 2, width = 1, height = 1, dim_unit = 'in',
    weight = 0.15, weight_unit = 'lb'
WHERE id IN ('a0000000-0000-4000-8000-000000000805', 'a0000000-0000-4000-8000-000000000806');
UPDATE product_variants SET
    length = 12, width = 10, height = 8, dim_unit = 'in',
    weight = 0.55, weight_unit = 'lb'
WHERE id IN ('a0000000-0000-4000-8000-000000000807', 'a0000000-0000-4000-8000-000000000808');

INSERT INTO shipping_cartons (id, tenant_id, name, length, width, height, max_weight, empty_weight, dim_unit, weight_unit) VALUES
    ('a0000000-0000-4000-8000-000000004101', 'a0000000-0000-4000-8000-000000000001',
     'Small Mailer', 8, 6, 4, 5, 0.15, 'in', 'lb'),
    ('a0000000-0000-4000-8000-000000004102', 'a0000000-0000-4000-8000-000000000001',
     'Medium Corrugated', 14, 10, 8, 30, 0.65, 'in', 'lb'),
    ('a0000000-0000-4000-8000-000000004103', 'a0000000-0000-4000-8000-000000000001',
     'Large Corrugated', 20, 16, 12, 70, 1.25, 'in', 'lb')
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO lots (id, tenant_id, variant_id, lot_number, expires_at, received_at) VALUES
    ('a0000000-0000-4000-8000-000000000901', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'LOT-2026-001', '2026-12-31', NOW() - INTERVAL '30 days'),
    ('a0000000-0000-4000-8000-000000000902', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'LOT-2026-002', '2027-06-30', NOW() - INTERVAL '10 days'),
    ('a0000000-0000-4000-8000-000000000903', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000803', 'LOT-GAD-01', NULL, NOW() - INTERVAL '5 days')
ON CONFLICT (tenant_id, variant_id, lot_number) DO NOTHING;

INSERT INTO suppliers (id, tenant_id, name, contact, payment_terms) VALUES
    ('a0000000-0000-4000-8000-000000001001', 'a0000000-0000-4000-8000-000000000001', 'Global Parts Inc', '{"email":"orders@globalparts.com"}', 'NET30'),
    ('a0000000-0000-4000-8000-000000001002', 'a0000000-0000-4000-8000-000000000001', 'FastSupply Co', '{"email":"sales@fastsupply.com"}', 'NET15')
ON CONFLICT DO NOTHING;

INSERT INTO customers (id, tenant_id, name, email, billing_address, shipping_address, stripe_customer_ref) VALUES
    ('a0000000-0000-4000-8000-000000001101', 'a0000000-0000-4000-8000-000000000001', 'Retail Partners LLC', 'ap@retailpartners.com', '{"city":"New York"}', '{"city":"New York"}', 'cus_demo_retail'),
    ('a0000000-0000-4000-8000-000000001102', 'a0000000-0000-4000-8000-000000000001', 'Metro Distributors', 'buyer@metrodist.com', '{"city":"Chicago"}', '{"city":"Chicago"}', 'cus_demo_metro')
ON CONFLICT DO NOTHING;

INSERT INTO customer_user_mappings (id, tenant_id, customer_id, user_id) VALUES
    ('a0000000-0000-4000-8000-000000000551', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001102', 'a0000000-0000-4000-8000-000000000206')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO supplier_user_mappings (id, tenant_id, supplier_id, user_id) VALUES
    ('a0000000-0000-4000-8000-000000000571', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001001', 'a0000000-0000-4000-8000-000000000209')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO customer_credit_lines (id, tenant_id, customer_id, credit_limit, available_credit, status) VALUES
    ('a0000000-0000-4000-8000-000000000561', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001102', 50000, 50000, 'ACTIVE')
ON CONFLICT (tenant_id, customer_id) DO NOTHING;

INSERT INTO document_sequences (id, tenant_id, doc_type, period, next_value) VALUES
    ('a0000000-0000-4000-8000-000000001201', 'a0000000-0000-4000-8000-000000000001', 'PO', '2026', 3),
    ('a0000000-0000-4000-8000-000000001202', 'a0000000-0000-4000-8000-000000000001', 'SO', '2026', 3),
    ('a0000000-0000-4000-8000-000000001203', 'a0000000-0000-4000-8000-000000000001', 'INVOICE', '2026', 3)
ON CONFLICT (tenant_id, doc_type, period) DO NOTHING;

INSERT INTO purchase_orders (id, tenant_id, supplier_id, number, status, expected_at) VALUES
    ('a0000000-0000-4000-8000-000000001301', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001001', 'PO-2026-00001', 'PARTIALLY_RECEIVED', NOW() + INTERVAL '5 days'),
    ('a0000000-0000-4000-8000-000000001302', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001002', 'PO-2026-00002', 'SUBMITTED', NOW() + INTERVAL '10 days')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO purchase_order_lines (id, tenant_id, purchase_order_id, variant_id, qty_ordered, qty_received, unit_cost) VALUES
    ('a0000000-0000-4000-8000-000000001401', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001301', 'a0000000-0000-4000-8000-000000000801', 100, 60, 8.00),
    ('a0000000-0000-4000-8000-000000001402', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001301', 'a0000000-0000-4000-8000-000000000805', 200, 0, 5.00),
    ('a0000000-0000-4000-8000-000000001403', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001302', 'a0000000-0000-4000-8000-000000000807', 500, 0, 1.50)
ON CONFLICT DO NOTHING;

INSERT INTO sales_orders (id, tenant_id, customer_id, number, status, channel) VALUES
    ('a0000000-0000-4000-8000-000000001501', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001101', 'SO-2026-00001', 'SHIPPED', 'DIRECT'),
    ('a0000000-0000-4000-8000-000000001502', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001102', 'SO-2026-00002', 'CONFIRMED', 'B2B')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO sales_order_lines (id, tenant_id, sales_order_id, variant_id, qty_ordered, qty_allocated, qty_shipped, unit_price, tax) VALUES
    ('a0000000-0000-4000-8000-000000001601', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001501', 'a0000000-0000-4000-8000-000000000801', 25, 25, 25, 12.50, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001602', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001501', 'a0000000-0000-4000-8000-000000000803', 10, 10, 0, 49.99, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001603', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001502', 'a0000000-0000-4000-8000-000000000807', 100, 0, 0, 2.25, '{"rate":0.08}')
ON CONFLICT DO NOTHING;

INSERT INTO inventory_ledger (id, tenant_id, variant_id, location_id, lot_id, movement_type, quantity_delta, reason_code, reference_type, reference_id, created_by) VALUES
    ('a0000000-0000-4000-8000-000000001701', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000901', 'RECEIVE', 60, 'PO_RECEIVE', 'PURCHASE_ORDER_LINE', 'a0000000-0000-4000-8000-000000001401', 'a0000000-0000-4000-8000-000000000203'),
    ('a0000000-0000-4000-8000-000000001702', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000902', 'RECEIVE', 40, 'PO_RECEIVE', 'PURCHASE_ORDER_LINE', 'a0000000-0000-4000-8000-000000001401', 'a0000000-0000-4000-8000-000000000203'),
    ('a0000000-0000-4000-8000-000000001703', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000803', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000903', 'RECEIVE', 50, 'ADJUST', 'MANUAL', NULL, 'a0000000-0000-4000-8000-000000000203'),
    ('a0000000-0000-4000-8000-000000001704', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000807', 'a0000000-0000-4000-8000-000000000605', NULL, 'RECEIVE', 300, 'ADJUST', 'MANUAL', NULL, 'a0000000-0000-4000-8000-000000000203'),
    ('a0000000-0000-4000-8000-000000001705', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000805', 'a0000000-0000-4000-8000-000000000613', NULL, 'RECEIVE', 150, 'ADJUST', 'MANUAL', NULL, 'a0000000-0000-4000-8000-000000000203')
ON CONFLICT DO NOTHING;

-- Service van stock (distinct ids — 1706/1707 are used later for SHIP ledger history)
INSERT INTO inventory_ledger (id, tenant_id, variant_id, location_id, lot_id, movement_type, quantity_delta, reason_code, reference_type, reference_id, created_by) VALUES
    ('a0000000-0000-4000-8000-000000001790', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000805', 'a0000000-0000-4000-8000-000000000621', NULL, 'RECEIVE', 12, 'VAN_REPLENISH', 'MANUAL', NULL, 'a0000000-0000-4000-8000-000000000203'),
    ('a0000000-0000-4000-8000-000000001791', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000806', 'a0000000-0000-4000-8000-000000000621', NULL, 'RECEIVE', 8, 'VAN_REPLENISH', 'MANUAL', NULL, 'a0000000-0000-4000-8000-000000000203')
ON CONFLICT DO NOTHING;

INSERT INTO allocations (id, tenant_id, sales_order_line_id, variant_id, location_id, lot_id, quantity, status) VALUES
    ('a0000000-0000-4000-8000-000000001801', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001601', 'a0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000901', 25, 'ACTIVE'),
    ('a0000000-0000-4000-8000-000000001802', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001602', 'a0000000-0000-4000-8000-000000000803', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000903', 10, 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO cycle_counts (id, tenant_id, location_id, status, notes, created_by) VALUES
    ('a0000000-0000-4000-8000-000000001901', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000604', 'IN_PROGRESS', 'Monthly bin count', 'a0000000-0000-4000-8000-000000000203')
ON CONFLICT DO NOTHING;

INSERT INTO cycle_count_lines (id, tenant_id, cycle_count_id, variant_id, lot_id, expected_qty, counted_qty) VALUES
    ('a0000000-0000-4000-8000-000000001911', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001901', 'a0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000901', 35, NULL),
    ('a0000000-0000-4000-8000-000000001912', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001901', 'a0000000-0000-4000-8000-000000000803', 'a0000000-0000-4000-8000-000000000903', 40, 40)
ON CONFLICT DO NOTHING;

INSERT INTO shipments (id, tenant_id, sales_order_id, carrier, tracking_number, label_ref, status) VALUES
    ('a0000000-0000-4000-8000-000000002001', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001501', NULL, NULL, NULL, 'PENDING')
ON CONFLICT DO NOTHING;

INSERT INTO shipment_lines (id, tenant_id, shipment_id, sales_order_line_id, quantity) VALUES
    ('a0000000-0000-4000-8000-000000002011', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002001', 'a0000000-0000-4000-8000-000000001601', 25)
ON CONFLICT DO NOTHING;

INSERT INTO invoices (id, tenant_id, sales_order_id, customer_id, number, status, subtotal, tax, total, currency, due_at) VALUES
    ('a0000000-0000-4000-8000-000000002101', 'a0000000-0000-4000-8000-000000000001', NULL, 'a0000000-0000-4000-8000-000000001101', 'INV-2026-00001', 'PAID', 812.40, 64.99, 877.39, 'USD', NOW() - INTERVAL '15 days'),
    ('a0000000-0000-4000-8000-000000002102', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001501', 'a0000000-0000-4000-8000-000000001101', 'INV-2026-00002', 'OPEN', 812.40, 64.99, 877.39, 'USD', NOW() + INTERVAL '30 days')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO invoice_lines (id, tenant_id, invoice_id, description, qty, unit_price, amount) VALUES
    ('a0000000-0000-4000-8000-000000002201', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002101', 'Widget Small x50', 50, 12.50, 625.00),
    ('a0000000-0000-4000-8000-000000002202', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002101', 'Gadget Black x5', 5, 49.99, 249.95),
    ('a0000000-0000-4000-8000-000000002203', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002102', 'Widget Small x25', 25, 12.50, 312.50),
    ('a0000000-0000-4000-8000-000000002204', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002102', 'Gadget Black x10', 10, 49.99, 499.90)
ON CONFLICT DO NOTHING;

INSERT INTO stripe_accounts (id, tenant_id, connected_account_id, onboarding_status, capabilities) VALUES
    ('a0000000-0000-4000-8000-000000002301', 'a0000000-0000-4000-8000-000000000001', 'acct_demo_connect_001', 'COMPLETE', '{"card_payments":"active"}')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO payment_intents (id, tenant_id, invoice_id, provider, external_id, amount, currency, application_fee_amount, connected_account_ref, status, raw_payload) VALUES
    ('a0000000-0000-4000-8000-000000002401', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002101', 'STRIPE', 'pi_demo_paid_001', 877.39, 'USD', 3.51, 'acct_demo_connect_001', 'SUCCEEDED', '{"mock":true}'),
    ('a0000000-0000-4000-8000-000000002402', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002102', 'STRIPE', 'pi_demo_open_002', 877.39, 'USD', 3.51, 'acct_demo_connect_001', 'PENDING', '{"mock":true}')
ON CONFLICT (provider, external_id) DO NOTHING;

INSERT INTO payments (id, tenant_id, payment_intent_id, amount, fee_amount, balance_txn_ref, settled_at) VALUES
    ('a0000000-0000-4000-8000-000000002501', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002401', 877.39, 3.51, 'txn_demo_001', NOW() - INTERVAL '10 days')
ON CONFLICT DO NOTHING;

INSERT INTO idempotency_keys (id, tenant_id, key, request_hash, response_status, response_body, expires_at) VALUES
    ('a0000000-0000-4000-8000-000000002601', 'a0000000-0000-4000-8000-000000000001', 'seed-idem-receive-001', 'abc123', 200, '{"status":"ok"}', NOW() + INTERVAL '24 hours')
ON CONFLICT (tenant_id, key) DO NOTHING;

INSERT INTO webhook_events (id, tenant_id, source, external_event_id, signature_valid, payload, received_at, processed_at) VALUES
    ('a0000000-0000-4000-8000-000000002701', 'a0000000-0000-4000-8000-000000000001', 'STRIPE', 'evt_demo_paid_001', true, '{"type":"payment_intent.succeeded"}', NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO outbox_events (id, tenant_id, aggregate_type, aggregate_id, event_type, payload, published_at) VALUES
    ('a0000000-0000-4000-8000-000000002801', 'a0000000-0000-4000-8000-000000000001', 'INVOICE', 'a0000000-0000-4000-8000-000000002101', 'INVOICE_PAID', '{"invoice_number":"INV-2026-00001"}', NOW() - INTERVAL '10 days'),
    ('a0000000-0000-4000-8000-000000002802', 'a0000000-0000-4000-8000-000000000001', 'SALES_ORDER', 'a0000000-0000-4000-8000-000000001501', 'ORDER_ALLOCATED', '{"order_number":"SO-2026-00001"}', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO external_references (id, tenant_id, entity_type, entity_id, system, external_id) VALUES
    ('a0000000-0000-4000-8000-000000002901', 'a0000000-0000-4000-8000-000000000001', 'CUSTOMER', 'a0000000-0000-4000-8000-000000001101', 'STRIPE', 'cus_demo_retail'),
    ('a0000000-0000-4000-8000-000000002902', 'a0000000-0000-4000-8000-000000000001', 'SALES_ORDER', 'a0000000-0000-4000-8000-000000001501', 'SHOPIFY', 'shopify_order_99001')
ON CONFLICT (tenant_id, system, external_id) DO NOTHING;

INSERT INTO audit_log (id, tenant_id, actor_user_id, action, entity_type, entity_id, diff) VALUES
    ('a0000000-0000-4000-8000-000000003001', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000201', 'CREATE', 'TENANT', 'a0000000-0000-4000-8000-000000000001', '{"name":"Demo Corp"}'),
    ('a0000000-0000-4000-8000-000000003002', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000203', 'RECEIVE', 'INVENTORY', 'a0000000-0000-4000-8000-000000001701', '{"qty":60}')
ON CONFLICT DO NOTHING;

-- Cost basis for valuation and profit reports
UPDATE product_variants SET avg_cost = CASE sku
    WHEN 'WIDGET-S' THEN 8.00
    WHEN 'WIDGET-L' THEN 9.50
    WHEN 'GADGET-BLK' THEN 32.00
    WHEN 'GADGET-WHT' THEN 32.00
    WHEN 'BOLT-M8-50' THEN 5.50
    WHEN 'TAPE-2IN' THEN 2.25
    WHEN 'BOX-MED' THEN 1.50
    WHEN 'BOX-LRG' THEN 2.10
    ELSE avg_cost
END WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001';

INSERT INTO document_sequences (id, tenant_id, doc_type, period, next_value) VALUES
    ('a0000000-0000-4000-8000-000000001204', 'a0000000-0000-4000-8000-000000000001', 'PRODUCTION', '2026', 5),
    ('a0000000-0000-4000-8000-000000001205', 'a0000000-0000-4000-8000-000000000001', 'RETURN', '2026', 4)
ON CONFLICT (tenant_id, doc_type, period) DO NOTHING;

-- Additional customers and suppliers
INSERT INTO customers (id, tenant_id, name, email, billing_address, shipping_address) VALUES
    ('a0000000-0000-4000-8000-000000001103', 'a0000000-0000-4000-8000-000000000001', 'Pacific Supply Co', 'orders@pacificsupply.com', '{"city":"Seattle"}', '{"city":"Seattle"}'),
    ('a0000000-0000-4000-8000-000000001104', 'a0000000-0000-4000-8000-000000000001', 'Summit Retail Group', 'buyers@summitretail.com', '{"city":"Denver"}', '{"city":"Denver"}')
ON CONFLICT DO NOTHING;

INSERT INTO suppliers (id, tenant_id, name, contact, payment_terms) VALUES
    ('a0000000-0000-4000-8000-000000001003', 'a0000000-0000-4000-8000-000000000001', 'Northwest Components', '{"email":"nw@components.com"}', 'NET30')
ON CONFLICT DO NOTHING;

-- Expanded purchase orders
INSERT INTO purchase_orders (id, tenant_id, supplier_id, number, status, expected_at, created_at) VALUES
    ('a0000000-0000-4000-8000-000000001303', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001001', 'PO-DEMO-00003', 'RECEIVED', NOW() - INTERVAL '45 days', NOW() - INTERVAL '50 days'),
    ('a0000000-0000-4000-8000-000000001304', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001003', 'PO-DEMO-00004', 'RECEIVED', NOW() - INTERVAL '20 days', NOW() - INTERVAL '25 days'),
    ('a0000000-0000-4000-8000-000000001305', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001002', 'PO-DEMO-00005', 'SUBMITTED', NOW() + INTERVAL '7 days', NOW() - INTERVAL '5 days')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO purchase_order_lines (id, tenant_id, purchase_order_id, variant_id, qty_ordered, qty_received, unit_cost) VALUES
    ('a0000000-0000-4000-8000-000000001404', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001303', 'a0000000-0000-4000-8000-000000000802', 75, 75, 9.50),
    ('a0000000-0000-4000-8000-000000001405', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001303', 'a0000000-0000-4000-8000-000000000804', 40, 40, 32.00),
    ('a0000000-0000-4000-8000-000000001406', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001304', 'a0000000-0000-4000-8000-000000000806', 120, 120, 2.25),
    ('a0000000-0000-4000-8000-000000001407', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001304', 'a0000000-0000-4000-8000-000000000808', 80, 80, 2.10),
    ('a0000000-0000-4000-8000-000000001408', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001305', 'a0000000-0000-4000-8000-000000000803', 60, 0, 32.00)
ON CONFLICT DO NOTHING;

-- Expanded sales orders across statuses (unique numbers to avoid conflicts with UI-created orders)
INSERT INTO sales_orders (id, tenant_id, customer_id, number, status, channel, created_at) VALUES
    ('a0000000-0000-4000-8000-000000001503', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001103', 'SO-DEMO-00003', 'ALLOCATED', 'DIRECT', NOW() - INTERVAL '12 days'),
    ('a0000000-0000-4000-8000-000000001504', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001104', 'SO-DEMO-00004', 'PARTIALLY_SHIPPED', 'B2B', NOW() - INTERVAL '25 days'),
    ('a0000000-0000-4000-8000-000000001505', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001101', 'SO-DEMO-00005', 'DRAFT', 'DIRECT', NOW() - INTERVAL '3 days'),
    ('a0000000-0000-4000-8000-000000001506', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001102', 'SO-DEMO-00006', 'CLOSED', 'B2B', NOW() - INTERVAL '60 days'),
    ('a0000000-0000-4000-8000-000000001507', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001103', 'SO-DEMO-00007', 'CONFIRMED', 'DIRECT', NOW() - INTERVAL '7 days')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO sales_order_lines (id, tenant_id, sales_order_id, variant_id, qty_ordered, qty_allocated, qty_shipped, unit_price, tax) VALUES
    ('a0000000-0000-4000-8000-000000001604', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001503', 'a0000000-0000-4000-8000-000000000802', 30, 30, 0, 15.00, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001605', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001504', 'a0000000-0000-4000-8000-000000000803', 20, 20, 12, 49.99, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001606', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001504', 'a0000000-0000-4000-8000-000000000807', 50, 50, 50, 2.25, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001607', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001505', 'a0000000-0000-4000-8000-000000000805', 40, 0, 0, 8.99, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001608', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001506', 'a0000000-0000-4000-8000-000000000801', 40, 40, 40, 12.50, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001609', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001506', 'a0000000-0000-4000-8000-000000000804', 8, 8, 8, 49.99, '{"rate":0.08}'),
    ('a0000000-0000-4000-8000-000000001610', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001507', 'a0000000-0000-4000-8000-000000000806', 60, 0, 0, 4.50, '{"rate":0.08}')
ON CONFLICT DO NOTHING;

-- Shipments and COGS ledger entries
INSERT INTO inventory_ledger (id, tenant_id, variant_id, location_id, lot_id, movement_type, quantity_delta, unit_cost, reason_code, reference_type, reference_id, created_by, created_at) VALUES
    ('a0000000-0000-4000-8000-000000001706', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000901', 'SHIP', -25, 8.00, 'SO_SHIP', 'SALES_ORDER_LINE', 'a0000000-0000-4000-8000-000000001601', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '20 days'),
    ('a0000000-0000-4000-8000-000000001707', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000902', 'SHIP', -40, 8.00, 'SO_SHIP', 'SALES_ORDER_LINE', 'a0000000-0000-4000-8000-000000001608', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '55 days'),
    ('a0000000-0000-4000-8000-000000001708', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000804', 'a0000000-0000-4000-8000-000000000604', NULL, 'SHIP', -8, 32.00, 'SO_SHIP', 'SALES_ORDER_LINE', 'a0000000-0000-4000-8000-000000001609', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '50 days'),
    ('a0000000-0000-4000-8000-000000001709', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000803', 'a0000000-0000-4000-8000-000000000604', 'a0000000-0000-4000-8000-000000000903', 'SHIP', -12, 32.00, 'SO_SHIP', 'SALES_ORDER_LINE', 'a0000000-0000-4000-8000-000000001605', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '18 days'),
    ('a0000000-0000-4000-8000-000000001710', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000807', 'a0000000-0000-4000-8000-000000000605', NULL, 'SHIP', -50, 1.50, 'SO_SHIP', 'SALES_ORDER_LINE', 'a0000000-0000-4000-8000-000000001606', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '15 days'),
    ('a0000000-0000-4000-8000-000000001711', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000802', 'a0000000-0000-4000-8000-000000000604', NULL, 'RECEIVE', 75, 9.50, 'PO_RECEIVE', 'PURCHASE_ORDER_LINE', 'a0000000-0000-4000-8000-000000001404', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '40 days'),
    ('a0000000-0000-4000-8000-000000001712', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000804', 'a0000000-0000-4000-8000-000000000604', NULL, 'RECEIVE', 40, 32.00, 'PO_RECEIVE', 'PURCHASE_ORDER_LINE', 'a0000000-0000-4000-8000-000000001405', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '38 days'),
    ('a0000000-0000-4000-8000-000000001713', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000806', 'a0000000-0000-4000-8000-000000000605', NULL, 'RECEIVE', 120, 2.25, 'PO_RECEIVE', 'PURCHASE_ORDER_LINE', 'a0000000-0000-4000-8000-000000001406', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '18 days'),
    ('a0000000-0000-4000-8000-000000001714', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000808', 'a0000000-0000-4000-8000-000000000605', NULL, 'RECEIVE', 80, 2.10, 'PO_RECEIVE', 'PURCHASE_ORDER_LINE', 'a0000000-0000-4000-8000-000000001407', 'a0000000-0000-4000-8000-000000000203', NOW() - INTERVAL '17 days')
ON CONFLICT DO NOTHING;

UPDATE sales_order_lines SET qty_shipped = 25 WHERE id = 'a0000000-0000-4000-8000-000000001601';
UPDATE sales_order_lines SET qty_shipped = 40 WHERE id = 'a0000000-0000-4000-8000-000000001608';
UPDATE sales_order_lines SET qty_shipped = 8 WHERE id = 'a0000000-0000-4000-8000-000000001609';

-- Additional invoices spread across months for revenue charts
INSERT INTO invoices (id, tenant_id, sales_order_id, customer_id, number, status, subtotal, tax, total, currency, due_at, created_at) VALUES
    ('a0000000-0000-4000-8000-000000002103', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001506', 'a0000000-0000-4000-8000-000000001101', 'INV-DEMO-00003', 'PAID', 899.92, 71.99, 971.91, 'USD', NOW() - INTERVAL '45 days', NOW() - INTERVAL '58 days'),
    ('a0000000-0000-4000-8000-000000002104', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001504', 'a0000000-0000-4000-8000-000000001104', 'INV-DEMO-00004', 'OPEN', 1112.30, 88.98, 1201.28, 'USD', NOW() + INTERVAL '20 days', NOW() - INTERVAL '20 days'),
    ('a0000000-0000-4000-8000-000000002105', 'a0000000-0000-4000-8000-000000000001', NULL, 'a0000000-0000-4000-8000-000000001103', 'INV-DEMO-00005', 'PAID', 450.00, 36.00, 486.00, 'USD', NOW() - INTERVAL '10 days', NOW() - INTERVAL '35 days')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO invoice_lines (id, tenant_id, invoice_id, description, qty, unit_price, amount) VALUES
    ('a0000000-0000-4000-8000-000000002205', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002103', 'Widget Small x40', 40, 12.50, 500.00),
    ('a0000000-0000-4000-8000-000000002206', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002103', 'Gadget White x8', 8, 49.99, 399.92),
    ('a0000000-0000-4000-8000-000000002207', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002104', 'Gadget Black x12', 12, 49.99, 599.88),
    ('a0000000-0000-4000-8000-000000002208', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002104', 'Shipping Box x50', 50, 2.25, 112.50),
    ('a0000000-0000-4000-8000-000000002209', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000002105', 'Widget Large x30', 30, 15.00, 450.00)
ON CONFLICT DO NOTHING;

-- Manufacturing: BOMs and production orders
INSERT INTO boms (id, tenant_id, parent_variant_id, name, is_active) VALUES
    ('a0000000-0000-4000-8000-000000003101', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000803', 'Smart Gadget Assembly', TRUE),
    ('a0000000-0000-4000-8000-000000003102', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000802', 'Widget Large Kit', TRUE)
ON CONFLICT (tenant_id, parent_variant_id) DO NOTHING;

INSERT INTO bom_lines (id, tenant_id, bom_id, component_variant_id, quantity_required) VALUES
    ('a0000000-0000-4000-8000-000000003111', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003101', 'a0000000-0000-4000-8000-000000000801', 2),
    ('a0000000-0000-4000-8000-000000003112', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003101', 'a0000000-0000-4000-8000-000000000805', 1),
    ('a0000000-0000-4000-8000-000000003113', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003101', 'a0000000-0000-4000-8000-000000000807', 1),
    ('a0000000-0000-4000-8000-000000003114', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003102', 'a0000000-0000-4000-8000-000000000801', 1),
    ('a0000000-0000-4000-8000-000000003115', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003102', 'a0000000-0000-4000-8000-000000000806', 2)
ON CONFLICT (bom_id, component_variant_id) DO NOTHING;

INSERT INTO production_orders (id, tenant_id, number, parent_variant_id, qty_target, qty_produced, status, created_at) VALUES
    ('a0000000-0000-4000-8000-000000003201', 'a0000000-0000-4000-8000-000000000001', 'MO-2026-00001', 'a0000000-0000-4000-8000-000000000803', 10, 0, 'DRAFT', NOW() - INTERVAL '4 days'),
    ('a0000000-0000-4000-8000-000000003202', 'a0000000-0000-4000-8000-000000000001', 'MO-2026-00002', 'a0000000-0000-4000-8000-000000000803', 15, 0, 'COMPONENTS_ALLOCATED', NOW() - INTERVAL '2 days'),
    ('a0000000-0000-4000-8000-000000003203', 'a0000000-0000-4000-8000-000000000001', 'MO-2026-00003', 'a0000000-0000-4000-8000-000000000802', 20, 8, 'WIP', NOW() - INTERVAL '6 days'),
    ('a0000000-0000-4000-8000-000000003204', 'a0000000-0000-4000-8000-000000000001', 'MO-2026-00004', 'a0000000-0000-4000-8000-000000000803', 5, 5, 'COMPLETED', NOW() - INTERVAL '20 days')
ON CONFLICT (tenant_id, number) DO NOTHING;

-- Returns for returns analysis report
INSERT INTO returns (id, tenant_id, sales_order_id, number, status, created_at) VALUES
    ('a0000000-0000-4000-8000-000000003301', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001501', 'RMA-DEMO-00001', 'RECEIVED', NOW() - INTERVAL '14 days'),
    ('a0000000-0000-4000-8000-000000003302', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001506', 'RMA-DEMO-00002', 'CLOSED', NOW() - INTERVAL '40 days'),
    ('a0000000-0000-4000-8000-000000003303', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001504', 'RMA-DEMO-00003', 'APPROVED', NOW() - INTERVAL '5 days')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO return_lines (id, tenant_id, return_id, sales_order_line_id, quantity_expected, quantity_received, disposition) VALUES
    ('a0000000-0000-4000-8000-000000003311', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003301', 'a0000000-0000-4000-8000-000000001601', 3, 3, 'RESTOCK'),
    ('a0000000-0000-4000-8000-000000003312', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003302', 'a0000000-0000-4000-8000-000000001608', 2, 2, 'RESTOCK'),
    ('a0000000-0000-4000-8000-000000003313', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003303', 'a0000000-0000-4000-8000-000000001605', 1, 0, NULL)
ON CONFLICT DO NOTHING;

-- =========================================================================
-- TENANT B: Acme Wholesale (isolation testing)
-- =========================================================================
SELECT set_config('app.current_tenant', 'b0000000-0000-4000-8000-000000000001', true);

INSERT INTO tenants (id, name, slug, status) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'Acme Wholesale', 'acme-wholesale', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenant_subscriptions (tenant_id, tier, enabled_modules, updated_at) VALUES
    ('b0000000-0000-4000-8000-000000000001', 'BASIC', '["CORE"]'::jsonb, NOW())
ON CONFLICT (tenant_id) DO UPDATE SET
    tier = EXCLUDED.tier,
    enabled_modules = EXCLUDED.enabled_modules,
    updated_at = NOW();

INSERT INTO tenant_settings (id, tenant_id, settings) VALUES
    ('b0000000-0000-4000-8000-000000000010', 'b0000000-0000-4000-8000-000000000001',
     '{"company_name":"Acme Wholesale","currency":"USD","timezone":"America/Chicago","allow_negative_inventory":false,"platform_fee_percent":0.4}'::jsonb)
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO roles (id, tenant_id, code, network_access_level) VALUES
    ('b0000000-0000-4000-8000-000000000101', 'b0000000-0000-4000-8000-000000000001', 'OWNER', 'MFA_OUTSIDE_NETWORK'),
    ('b0000000-0000-4000-8000-000000000102', 'b0000000-0000-4000-8000-000000000001', 'ADMIN', 'MFA_OUTSIDE_NETWORK'),
    ('b0000000-0000-4000-8000-000000000103', 'b0000000-0000-4000-8000-000000000001', 'WAREHOUSE_MANAGER', 'MFA_OUTSIDE_NETWORK'),
    ('b0000000-0000-4000-8000-000000000104', 'b0000000-0000-4000-8000-000000000001', 'PICKER', 'STRICT_INTERNAL'),
    ('b0000000-0000-4000-8000-000000000105', 'b0000000-0000-4000-8000-000000000001', 'VIEWER', 'MFA_OUTSIDE_NETWORK')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO users (id, tenant_id, email, password_hash, display_name, status) VALUES
    ('b0000000-0000-4000-8000-000000000201', 'b0000000-0000-4000-8000-000000000001', 'owner@acme.test', '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu', 'Acme Owner', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (id, tenant_id, user_id, role_id) VALUES
    ('b0000000-0000-4000-8000-000000000301', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000201', 'b0000000-0000-4000-8000-000000000101')
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO refresh_tokens (id, tenant_id, user_id, token_hash, expires_at) VALUES
    ('b0000000-0000-4000-8000-000000000401', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000201', 'acme_refresh_hash', NOW() + INTERVAL '7 days')
ON CONFLICT (token_hash) DO NOTHING;

INSERT INTO invitations (id, tenant_id, email, role_id, token_hash, invited_by, expires_at) VALUES
    ('b0000000-0000-4000-8000-000000000501', 'b0000000-0000-4000-8000-000000000001', 'newhire@acme.test', 'b0000000-0000-4000-8000-000000000104', 'acme_invite_hash', 'b0000000-0000-4000-8000-000000000201', NOW() + INTERVAL '7 days')
ON CONFLICT (token_hash) DO NOTHING;

INSERT INTO locations (id, tenant_id, parent_location_id, type, code, name, path) VALUES
    ('b0000000-0000-4000-8000-000000000601', 'b0000000-0000-4000-8000-000000000001', NULL, 'WAREHOUSE', 'WH-01', 'Acme Warehouse', 'WH-01')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO products (id, tenant_id, sku_root, name, description) VALUES
    ('b0000000-0000-4000-8000-000000000701', 'b0000000-0000-4000-8000-000000000001', 'ACME-01', 'Acme Product', 'Tenant B only')
ON CONFLICT (tenant_id, sku_root) DO NOTHING;

INSERT INTO product_variants (id, tenant_id, product_id, sku, barcode, attributes, price, currency) VALUES
    ('b0000000-0000-4000-8000-000000000801', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000701', 'ACME-01-STD', '9902000000001', '{}', 99.00, 'USD')
ON CONFLICT (tenant_id, sku) DO NOTHING;

INSERT INTO lots (id, tenant_id, variant_id, lot_number, expires_at) VALUES
    ('b0000000-0000-4000-8000-000000000901', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000801', 'ACME-LOT-1', NULL)
ON CONFLICT (tenant_id, variant_id, lot_number) DO NOTHING;

INSERT INTO suppliers (id, tenant_id, name, contact) VALUES
    ('b0000000-0000-4000-8000-000000001001', 'b0000000-0000-4000-8000-000000000001', 'Acme Supplier', '{}')
ON CONFLICT DO NOTHING;

INSERT INTO customers (id, tenant_id, name, email, billing_address, shipping_address) VALUES
    ('b0000000-0000-4000-8000-000000001101', 'b0000000-0000-4000-8000-000000000001', 'Acme Customer', 'buyer@acme-customer.com', '{}', '{}')
ON CONFLICT DO NOTHING;

INSERT INTO document_sequences (id, tenant_id, doc_type, period, next_value) VALUES
    ('b0000000-0000-4000-8000-000000001201', 'b0000000-0000-4000-8000-000000000001', 'INVOICE', '2026', 2)
ON CONFLICT (tenant_id, doc_type, period) DO NOTHING;

INSERT INTO purchase_orders (id, tenant_id, supplier_id, number, status) VALUES
    ('b0000000-0000-4000-8000-000000001301', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000001001', 'PO-ACME-001', 'DRAFT')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO purchase_order_lines (id, tenant_id, purchase_order_id, variant_id, qty_ordered, unit_cost) VALUES
    ('b0000000-0000-4000-8000-000000001401', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000001301', 'b0000000-0000-4000-8000-000000000801', 50, 75.00)
ON CONFLICT DO NOTHING;

INSERT INTO sales_orders (id, tenant_id, customer_id, number, status) VALUES
    ('b0000000-0000-4000-8000-000000001501', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000001101', 'SO-ACME-001', 'DRAFT')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO sales_order_lines (id, tenant_id, sales_order_id, variant_id, qty_ordered, unit_price) VALUES
    ('b0000000-0000-4000-8000-000000001601', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000001501', 'b0000000-0000-4000-8000-000000000801', 5, 99.00)
ON CONFLICT DO NOTHING;

INSERT INTO inventory_ledger (id, tenant_id, variant_id, location_id, lot_id, movement_type, quantity_delta, reason_code, created_by) VALUES
    ('b0000000-0000-4000-8000-000000001701', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000801', 'b0000000-0000-4000-8000-000000000601', 'b0000000-0000-4000-8000-000000000901', 'RECEIVE', 25, 'ADJUST', 'b0000000-0000-4000-8000-000000000201')
ON CONFLICT DO NOTHING;

INSERT INTO cycle_counts (id, tenant_id, location_id, status, created_by) VALUES
    ('b0000000-0000-4000-8000-000000001901', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000601', 'DRAFT', 'b0000000-0000-4000-8000-000000000201')
ON CONFLICT DO NOTHING;

INSERT INTO cycle_count_lines (id, tenant_id, cycle_count_id, variant_id, expected_qty) VALUES
    ('b0000000-0000-4000-8000-000000001911', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000001901', 'b0000000-0000-4000-8000-000000000801', 25)
ON CONFLICT DO NOTHING;

INSERT INTO invoices (id, tenant_id, customer_id, number, status, subtotal, tax, total, currency) VALUES
    ('b0000000-0000-4000-8000-000000002101', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000001101', 'INV-ACME-001', 'DRAFT', 495.00, 0, 495.00, 'USD')
ON CONFLICT (tenant_id, number) DO NOTHING;

INSERT INTO invoice_lines (id, tenant_id, invoice_id, description, qty, unit_price, amount) VALUES
    ('b0000000-0000-4000-8000-000000002201', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000002101', 'Acme Product x5', 5, 99.00, 495.00)
ON CONFLICT DO NOTHING;

INSERT INTO stripe_accounts (id, tenant_id, connected_account_id, onboarding_status, capabilities) VALUES
    ('b0000000-0000-4000-8000-000000002301', 'b0000000-0000-4000-8000-000000000001', 'acct_acme_001', 'PENDING', '{}')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO payment_intents (id, tenant_id, invoice_id, provider, external_id, amount, currency, application_fee_amount, status) VALUES
    ('b0000000-0000-4000-8000-000000002401', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000002101', 'STRIPE', 'pi_acme_draft', 495.00, 'USD', 1.98, 'PENDING')
ON CONFLICT (provider, external_id) DO NOTHING;

INSERT INTO idempotency_keys (id, tenant_id, key, expires_at) VALUES
    ('b0000000-0000-4000-8000-000000002601', 'b0000000-0000-4000-8000-000000000001', 'acme-idem-1', NOW() + INTERVAL '24 hours')
ON CONFLICT (tenant_id, key) DO NOTHING;

INSERT INTO webhook_events (id, tenant_id, source, external_event_id, signature_valid, payload) VALUES
    ('b0000000-0000-4000-8000-000000002701', 'b0000000-0000-4000-8000-000000000001', 'STRIPE', 'evt_acme_001', true, '{}')
ON CONFLICT (id) DO NOTHING;

INSERT INTO outbox_events (id, tenant_id, aggregate_type, aggregate_id, event_type, payload) VALUES
    ('b0000000-0000-4000-8000-000000002801', 'b0000000-0000-4000-8000-000000000001', 'TENANT', 'b0000000-0000-4000-8000-000000000001', 'TENANT_CREATED', '{}')
ON CONFLICT DO NOTHING;

INSERT INTO external_references (id, tenant_id, entity_type, entity_id, system, external_id) VALUES
    ('b0000000-0000-4000-8000-000000002901', 'b0000000-0000-4000-8000-000000000001', 'PRODUCT', 'b0000000-0000-4000-8000-000000000701', 'SHOPIFY', 'shopify_prod_acme_1')
ON CONFLICT (tenant_id, system, external_id) DO NOTHING;

INSERT INTO audit_log (id, tenant_id, actor_user_id, action, entity_type, entity_id, diff) VALUES
    ('b0000000-0000-4000-8000-000000003001', 'b0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000201', 'CREATE', 'TENANT', 'b0000000-0000-4000-8000-000000000001', '{"name":"Acme Wholesale"}')
ON CONFLICT DO NOTHING;

-- Re-apply correct bcrypt for password123 after seed (idempotent)
SELECT set_config('app.current_tenant', 'a0000000-0000-4000-8000-000000000001', false);
UPDATE users SET password_hash = '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001';

SELECT set_config('app.current_tenant', 'b0000000-0000-4000-8000-000000000001', false);
UPDATE users SET password_hash = '$2a$10$ahiY2Lk.l8HTqZTO0gMhO.W/cqEDtYSE0uQrfxqhL9Ewl0Oee8sSu'
WHERE tenant_id = 'b0000000-0000-4000-8000-000000000001';

-- Track 14-16 production readiness seed (demo tenant)
SELECT set_config('app.current_tenant', 'a0000000-0000-4000-8000-000000000001', true);

INSERT INTO tax_rates (id, tenant_id, name, rate, is_default) VALUES
    ('a0000000-0000-4000-8000-000000003101', 'a0000000-0000-4000-8000-000000000001', 'NY Sales Tax', 0.0888, true),
    ('a0000000-0000-4000-8000-000000003102', 'a0000000-0000-4000-8000-000000000001', 'Exempt', 0.0000, false)
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO tenant_domains (id, tenant_id, domain_name, verification_status, dkim_tokens) VALUES
    ('a0000000-0000-4000-8000-000000003201', 'a0000000-0000-4000-8000-000000000001', 'mail.democorp.test', 'VERIFIED',
     '[{"type":"CNAME","host":"dkim1._domainkey.mail.democorp.test","value":"dkim1.invsys.mail.example"}]'::jsonb)
ON CONFLICT (tenant_id, domain_name) DO NOTHING;

INSERT INTO demand_forecasts (id, tenant_id, variant_id, recommended_po_qty, velocity_30d, calculated_at) VALUES
    ('a0000000-0000-4000-8000-000000003301', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000803', 45, 1.5, NOW()),
    ('a0000000-0000-4000-8000-000000000302', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000805', 120, 4.0, NOW())
ON CONFLICT (tenant_id, variant_id) DO NOTHING;

INSERT INTO edi_trading_partners (id, tenant_id, customer_id, supplier_id, as2_id) VALUES
    ('a0000000-0000-4000-8000-000000003401', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000001101', NULL, 'RETAILPARTNERS_AS2')
ON CONFLICT (tenant_id, as2_id) DO NOTHING;

UPDATE product_variants SET
    default_supplier_id = 'a0000000-0000-4000-8000-000000001001',
    supplier_lead_time_days = 14,
    weight = 0.45,
    weight_unit = 'kg'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001' AND sku IN ('GADGET-BLK', 'GADGET-WHT');

UPDATE product_variants SET
    default_supplier_id = 'a0000000-0000-4000-8000-000000001002',
    supplier_lead_time_days = 7,
    weight = 0.12,
    weight_unit = 'kg'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001' AND sku IN ('BOLT-M8-50', 'TAPE-2IN');

UPDATE product_variants SET
    default_location_id = 'a0000000-0000-4000-8000-000000000604',
    is_kit = TRUE
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001' AND sku = 'WIDGET-L';

UPDATE product_variants SET
    default_location_id = 'a0000000-0000-4000-8000-000000000605'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001' AND sku = 'WIDGET-S';

INSERT INTO variant_uom_conversions (id, tenant_id, variant_id, uom_type, unit_name, conversion_ratio) VALUES
    ('a0000000-0000-4000-8000-000000003501', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'STANDARD', 'EA', 1),
    ('a0000000-0000-4000-8000-000000003502', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'PURCHASING', 'Case', 24),
    ('a0000000-0000-4000-8000-000000003503', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000801', 'SALES', 'Pack', 6)
ON CONFLICT (tenant_id, variant_id, uom_type) DO NOTHING;

UPDATE boms SET auto_assemble = TRUE
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001'
  AND parent_variant_id = 'a0000000-0000-4000-8000-000000000802';

INSERT INTO manufacturing_operations (id, tenant_id, name, default_hourly_rate) VALUES
    ('a0000000-0000-4000-8000-000000003601', 'a0000000-0000-4000-8000-000000000001', 'Assembly', 35.00),
    ('a0000000-0000-4000-8000-000000003602', 'a0000000-0000-4000-8000-000000000001', 'Quality check', 28.00)
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO bom_operations (id, tenant_id, bom_id, operation_id, estimated_hours) VALUES
    ('a0000000-0000-4000-8000-000000003611', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003102', 'a0000000-0000-4000-8000-000000003601', 0.25),
    ('a0000000-0000-4000-8000-000000003612', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000003101', 'a0000000-0000-4000-8000-000000003601', 0.50)
ON CONFLICT (bom_id, operation_id) DO NOTHING;

INSERT INTO team_labor_rates (id, tenant_id, user_id, hourly_rate) VALUES
    ('a0000000-0000-4000-8000-000000003621', 'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000204', 32.00)
ON CONFLICT (tenant_id, user_id) DO NOTHING;

-- 3PL billing SLAs (Metro = pallet positions, Retail = cubic volume)
INSERT INTO billing_slas (id, tenant_id, customer_id, storage_mode, rate_per_unit, pick_fee_per_item) VALUES
    ('a0000000-0000-4000-8000-000000004201', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001102', 'PALLET_POSITION', 1.25, 0.35),
    ('a0000000-0000-4000-8000-000000004202', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001101', 'CUBIC_VOLUME', 0.05, 0.25)
ON CONFLICT (tenant_id, customer_id) DO NOTHING;

-- Attribute on-hand stock to 3PL owners for accrual demos
UPDATE inventory_levels SET owner_customer_id = 'a0000000-0000-4000-8000-000000001102'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001'
  AND location_id IN (
      'a0000000-0000-4000-8000-000000000604',
      'a0000000-0000-4000-8000-000000000605'
  )
  AND on_hand > 0;

UPDATE inventory_levels SET owner_customer_id = 'a0000000-0000-4000-8000-000000001101'
WHERE tenant_id = 'a0000000-0000-4000-8000-000000000001'
  AND location_id = 'a0000000-0000-4000-8000-000000000613'
  AND on_hand > 0;

UPDATE product_variants SET volume = 0.05
WHERE id IN (
    'a0000000-0000-4000-8000-000000000801',
    'a0000000-0000-4000-8000-000000000802',
    'a0000000-0000-4000-8000-000000000803'
);

INSERT INTO billing_accruals (id, tenant_id, customer_id, accrual_date, amount, description, status) VALUES
    ('a0000000-0000-4000-8000-000000004301', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001102', CURRENT_DATE - 2, 2.50, 'Daily storage accrual', 'UNBILLED'),
    ('a0000000-0000-4000-8000-000000004302', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001102', CURRENT_DATE - 1, 2.50, 'Daily storage accrual', 'UNBILLED'),
    ('a0000000-0000-4000-8000-000000004303', 'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000001101', CURRENT_DATE - 1, 1.15, 'Daily storage accrual', 'UNBILLED')
-- Prefer PK: accrual_date uses CURRENT_DATE offsets, so the business unique key
-- can miss on re-seed while fixed UUIDs still collide on id.
ON CONFLICT (id) DO NOTHING;

COMMIT;

-- inventory_levels is trigger-maintained (no direct inserts)
-- Verify: SELECT COUNT(*) FROM each table after seeding
