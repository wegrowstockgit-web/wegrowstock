-- V109: Database-driven commercial tier packaging (Control Plane managed).

CREATE TABLE platform_tier_definitions (
    tier_code        VARCHAR(50) PRIMARY KEY,
    display_name     VARCHAR(100) NOT NULL,
    default_modules  JSONB NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE platform_tier_definitions IS
    'Commercial tier bundles. Super Admin mutates default_modules; WMS nodes cache the mapping.';
COMMENT ON COLUMN platform_tier_definitions.tier_code IS
    'CommercialTier code: BASIC | INTERMEDIATE | ENTERPRISE';
COMMENT ON COLUMN platform_tier_definitions.default_modules IS
    'AppModule JSON array included in the base price of this tier.';

CREATE TRIGGER platform_tier_definitions_updated_at BEFORE UPDATE ON platform_tier_definitions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO platform_tier_definitions (tier_code, display_name, default_modules, updated_at)
VALUES
    ('BASIC', 'Basic',
     '["CORE"]'::jsonb,
     NOW()),
    ('INTERMEDIATE', 'Intermediate',
     '["CORE","SHOPIFY","ACCOUNTING","ADVANCED_FULFILLMENT","MANUFACTURING","DOCUMENTS","MRP"]'::jsonb,
     NOW()),
    ('ENTERPRISE', 'Enterprise',
     '["CORE","SHOPIFY","ACCOUNTING","ADVANCED_FULFILLMENT","MANUFACTURING","DOCUMENTS","MRP","B2B_SHOWROOM","FINTECH","MESH_NETWORK","RTLS_TELEMETRY","AI_COPILOT"]'::jsonb,
     NOW());

GRANT SELECT, INSERT, UPDATE, DELETE ON platform_tier_definitions TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_tier_definitions TO app_user;
