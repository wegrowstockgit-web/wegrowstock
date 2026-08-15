-- V105: Expand commercial AppModule catalog documentation + backfill new keys for ENTERPRISE tenants.
-- Additive only — existing enabled_modules tokens remain valid; new enums are opt-in via control plane.

COMMENT ON COLUMN tenant_subscriptions.enabled_modules IS
    'AppModule JSON array. Tier1: CORE. Tier2: +SHOPIFY,ACCOUNTING,ADVANCED_FULFILLMENT,MANUFACTURING,DOCUMENTS,MRP. Tier3: +B2B_SHOWROOM,FINTECH,MESH_NETWORK,RTLS_TELEMETRY,AI_COPILOT';

-- ENTERPRISE rows that still carry the V104 module set get the full catalog.
UPDATE tenant_subscriptions
SET enabled_modules = '["CORE","SHOPIFY","ACCOUNTING","ADVANCED_FULFILLMENT","MANUFACTURING","DOCUMENTS","MRP","B2B_SHOWROOM","FINTECH","MESH_NETWORK","RTLS_TELEMETRY","AI_COPILOT"]'::jsonb,
    updated_at = NOW()
WHERE tier = 'ENTERPRISE';
