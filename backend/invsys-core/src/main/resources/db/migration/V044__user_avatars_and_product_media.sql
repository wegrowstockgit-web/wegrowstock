-- V044: User avatars + product media (RLS-enforced)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(1024) DEFAULT NULL;

COMMENT ON COLUMN users.avatar_url IS 'Profile image URL for office/warehouse UI avatar rendering.';

CREATE TABLE IF NOT EXISTS product_media (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    variant_id  UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    url         VARCHAR(1024) NOT NULL,
    is_primary  BOOLEAN NOT NULL DEFAULT false,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_media_tenant_variant
    ON product_media (tenant_id, variant_id);

CREATE INDEX IF NOT EXISTS idx_product_media_primary
    ON product_media (tenant_id, variant_id)
    WHERE is_primary = true;

ALTER TABLE product_media ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_media FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON product_media;
CREATE POLICY tenant_isolation ON product_media
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON product_media TO app_user;
GRANT SELECT ON product_media TO app_owner;

DROP TRIGGER IF EXISTS product_media_updated_at ON product_media;
CREATE TRIGGER product_media_updated_at BEFORE UPDATE ON product_media
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
