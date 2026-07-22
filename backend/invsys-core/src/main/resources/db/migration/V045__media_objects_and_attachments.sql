-- V045: Secure binary media objects + polymorphic evidence attachments (RLS)

CREATE TABLE IF NOT EXISTS media_objects (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    storage_key       VARCHAR(512) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    byte_size         BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 15728640),
    checksum_sha256   VARCHAR(64) NOT NULL,
    original_filename VARCHAR(255),
    created_by        UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_media_objects_tenant_key UNIQUE (tenant_id, storage_key)
);

CREATE INDEX IF NOT EXISTS idx_media_objects_tenant_created
    ON media_objects (tenant_id, created_at DESC);

ALTER TABLE media_objects ENABLE ROW LEVEL SECURITY;
ALTER TABLE media_objects FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON media_objects;
CREATE POLICY tenant_isolation ON media_objects
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON media_objects TO app_user;
GRANT SELECT ON media_objects TO app_owner;

DROP TRIGGER IF EXISTS media_objects_updated_at ON media_objects;
CREATE TRIGGER media_objects_updated_at BEFORE UPDATE ON media_objects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS media_attachments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    media_object_id UUID NOT NULL REFERENCES media_objects(id) ON DELETE CASCADE,
    entity_type     VARCHAR(40) NOT NULL,
    entity_id       UUID NOT NULL,
    purpose         VARCHAR(40) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_media_attachments_entity_type CHECK (entity_type IN (
        'USER', 'PRODUCT_VARIANT', 'RETURN_LINE', 'PURCHASE_ORDER_LINE',
        'LOCATION', 'PRODUCTION_ORDER'
    )),
    CONSTRAINT chk_media_attachments_purpose CHECK (purpose IN (
        'AVATAR', 'PRIMARY', 'GALLERY', 'QC_DAMAGE', 'RETURN_CONDITION',
        'LOCATION', 'RECEIVE_EVIDENCE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_media_attachments_entity
    ON media_attachments (tenant_id, entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_media_attachments_media
    ON media_attachments (tenant_id, media_object_id);

ALTER TABLE media_attachments ENABLE ROW LEVEL SECURITY;
ALTER TABLE media_attachments FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON media_attachments;
CREATE POLICY tenant_isolation ON media_attachments
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON media_attachments TO app_user;
GRANT SELECT ON media_attachments TO app_owner;

DROP TRIGGER IF EXISTS media_attachments_updated_at ON media_attachments;
CREATE TRIGGER media_attachments_updated_at BEFORE UPDATE ON media_attachments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE media_objects IS 'Tenant-scoped binary image blobs (local disk / object store).';
COMMENT ON TABLE media_attachments IS 'Polymorphic links from media objects to domain entities.';
