-- V070: Per-user saved grid layouts (column order / pin / visibility)
-- Prompt referenced V060; that slot is offline_sync_conflicts.

CREATE TABLE user_saved_views (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grid_identifier  VARCHAR(50) NOT NULL,
    name             VARCHAR(100) NOT NULL,
    state_json       JSONB NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, user_id, grid_identifier, name)
);

CREATE INDEX idx_user_saved_views_user_grid
    ON user_saved_views (tenant_id, user_id, grid_identifier);

CREATE TRIGGER user_saved_views_updated_at BEFORE UPDATE ON user_saved_views
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE user_saved_views ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_saved_views FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON user_saved_views
    USING (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = nullif(current_setting('app.current_tenant', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_saved_views TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_saved_views TO app_owner;
