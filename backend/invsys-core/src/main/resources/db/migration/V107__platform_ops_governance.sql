-- Platform ops governance: shard routing, integration kill-switch, rate overrides, compliance broadcasts.

CREATE TABLE IF NOT EXISTS tenant_shard_routing (
    tenant_id       UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    shard_key       VARCHAR(64) NOT NULL DEFAULT 'primary',
    jdbc_url        TEXT,
    aurora_cluster  VARCHAR(128),
    region          VARCHAR(32) NOT NULL DEFAULT 'us-east-1',
    notes           TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      UUID
);

CREATE TABLE IF NOT EXISTS tenant_integration_controls (
    tenant_id           UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    sync_paused         BOOLEAN NOT NULL DEFAULT FALSE,
    paused_reason       TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by          UUID
);

CREATE TABLE IF NOT EXISTS tenant_rate_limit_overrides (
    tenant_id           UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    capacity_multiplier NUMERIC(8,3) NOT NULL DEFAULT 1.0 CHECK (capacity_multiplier > 0),
    auth_per_minute     INT,
    webhook_per_minute  INT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by          UUID
);

CREATE TABLE IF NOT EXISTS platform_compliance_broadcasts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category        VARCHAR(64) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    payload_json    JSONB NOT NULL DEFAULT '{}'::jsonb,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID
);

CREATE TABLE IF NOT EXISTS platform_knowledge_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL UNIQUE,
    content_md      TEXT NOT NULL,
    chunk_count     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID
);

CREATE TABLE IF NOT EXISTS platform_sandbox_credentials (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    sandbox_tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    api_key_hash        VARCHAR(128) NOT NULL,
    api_key_hint        VARCHAR(16) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    CONSTRAINT platform_sandbox_source_uq UNIQUE (source_tenant_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_shard_routing TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_integration_controls TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON tenant_rate_limit_overrides TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_compliance_broadcasts TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_knowledge_documents TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON platform_sandbox_credentials TO app_owner;

GRANT SELECT ON tenant_shard_routing TO app_user;
GRANT SELECT ON tenant_integration_controls TO app_user;
GRANT SELECT ON tenant_rate_limit_overrides TO app_user;
GRANT SELECT ON platform_compliance_broadcasts TO app_user;
GRANT SELECT ON platform_knowledge_documents TO app_user;
