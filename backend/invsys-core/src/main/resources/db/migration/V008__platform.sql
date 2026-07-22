CREATE TABLE document_sequences (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    doc_type    VARCHAR(20) NOT NULL,
    period      VARCHAR(10) NOT NULL DEFAULT '',
    next_value  BIGINT NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, doc_type, period)
);

CREATE TRIGGER document_sequences_updated_at BEFORE UPDATE ON document_sequences
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE idempotency_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    key             VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64),
    response_status INT,
    response_body   JSONB,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, key)
);

CREATE TRIGGER idempotency_keys_updated_at BEFORE UPDATE ON idempotency_keys
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE webhook_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID REFERENCES tenants(id) ON DELETE CASCADE,
    source              VARCHAR(50) NOT NULL,
    external_event_id   VARCHAR(255) NOT NULL,
    signature_valid     BOOLEAN NOT NULL DEFAULT FALSE,
    payload             JSONB NOT NULL DEFAULT '{}',
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    error               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source, external_event_id)
);

CREATE TRIGGER webhook_events_updated_at BEFORE UPDATE ON webhook_events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL DEFAULT '{}',
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(tenant_id, created_at) WHERE published_at IS NULL;
CREATE TRIGGER outbox_events_updated_at BEFORE UPDATE ON outbox_events
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE external_references (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID NOT NULL,
    system      VARCHAR(50) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, system, external_id)
);

CREATE TRIGGER external_references_updated_at BEFORE UPDATE ON external_references
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    actor_user_id   UUID REFERENCES users(id),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    diff            JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_tenant_created ON audit_log(tenant_id, created_at DESC);
CREATE TRIGGER audit_log_updated_at BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
