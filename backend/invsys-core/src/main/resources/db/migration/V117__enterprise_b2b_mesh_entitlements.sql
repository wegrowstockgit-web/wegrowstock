-- ENTERPRISE (Tier 3) includes B2B showroom + mesh. BASIC / INTERMEDIATE stay on
-- their tier bundles. Idempotent backfill for Demo Corp and any other ENTERPRISE
-- tenant that was seeded before those modules existed.

UPDATE tenant_subscriptions
SET enabled_modules = enabled_modules
        || CASE WHEN enabled_modules ? 'B2B_SHOWROOM' THEN '[]'::jsonb ELSE '["B2B_SHOWROOM"]'::jsonb END
        || CASE WHEN enabled_modules ? 'MESH_NETWORK' THEN '[]'::jsonb ELSE '["MESH_NETWORK"]'::jsonb END,
    updated_at = NOW()
WHERE tier = 'ENTERPRISE';
