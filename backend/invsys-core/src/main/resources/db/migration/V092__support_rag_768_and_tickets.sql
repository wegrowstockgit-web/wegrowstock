-- Resize support RAG embeddings to 768 (text-embedding-004) and align with Spring AI PgVectorStore.
-- Extra catalog columns (slug/title/body/audience_roles/route_hints) remain for GraphRAG filters.

DROP INDEX IF EXISTS idx_support_knowledge_embedding;

ALTER TABLE support_knowledge_chunks
    ADD COLUMN IF NOT EXISTS content TEXT,
    ADD COLUMN IF NOT EXISTS metadata JSON DEFAULT '{}'::json;

UPDATE support_knowledge_chunks
   SET content = body
 WHERE content IS NULL;

ALTER TABLE support_knowledge_chunks
    ALTER COLUMN content SET NOT NULL;

-- Empty + recreate embedding column at new dimensionality (seed/ingest repopulates).
TRUNCATE TABLE support_knowledge_chunks;

ALTER TABLE support_knowledge_chunks DROP COLUMN IF EXISTS embedding;
ALTER TABLE support_knowledge_chunks ADD COLUMN embedding vector(768) NOT NULL;

CREATE INDEX idx_support_knowledge_embedding
    ON support_knowledge_chunks USING hnsw (embedding vector_cosine_ops);

COMMENT ON COLUMN support_knowledge_chunks.content IS
    'Spring AI PgVectorStore document text (mirrors body for GraphRAG)';
COMMENT ON COLUMN support_knowledge_chunks.metadata IS
    'Spring AI metadata JSON (audience_roles, route_hints, slug, title, source_path)';
COMMENT ON COLUMN support_knowledge_chunks.embedding IS
    '768-dim embedding (text-embedding-004 / HashEmbeddingModel)';

-- Human escalation tickets created by Support Co-Pilot escalateToHumanSupport tool.
CREATE TABLE support_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    opened_by       UUID,
    session_id      TEXT,
    route           TEXT,
    user_role       TEXT,
    subject         TEXT NOT NULL,
    summary         TEXT NOT NULL,
    status          TEXT NOT NULL DEFAULT 'OPEN',
    priority        TEXT NOT NULL DEFAULT 'NORMAL',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_tickets_status_chk
        CHECK (status IN ('OPEN', 'ASSIGNED', 'RESOLVED', 'CLOSED')),
    CONSTRAINT support_tickets_priority_chk
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'))
);

CREATE INDEX idx_support_tickets_tenant_status
    ON support_tickets (tenant_id, status, created_at DESC);

ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;

CREATE POLICY support_tickets_tenant_isolation ON support_tickets
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON support_tickets TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON support_tickets TO app_user;

COMMENT ON TABLE support_tickets IS
    'L1/L2 human handoff tickets opened by Support Co-Pilot escalation tool';
