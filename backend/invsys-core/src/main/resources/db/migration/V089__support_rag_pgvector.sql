-- Platform support RAG knowledge base (pgvector). Global docs — not tenant-secret.
-- Extension `vector` is created by ops/postgres/init (superuser) / db/test-init.sql;
-- app_owner cannot CREATE EXTENSION.

CREATE TABLE support_knowledge_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            TEXT NOT NULL UNIQUE,
    title           TEXT NOT NULL,
    body            TEXT NOT NULL,
    audience_roles  TEXT[] NOT NULL DEFAULT '{}',
    route_hints     TEXT[] NOT NULL DEFAULT '{}',
    source_path     TEXT,
    embedding       vector(384) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_knowledge_embedding
    ON support_knowledge_chunks USING hnsw (embedding vector_cosine_ops);

CREATE INDEX idx_support_knowledge_roles
    ON support_knowledge_chunks USING gin (audience_roles);

GRANT SELECT, INSERT, UPDATE, DELETE ON support_knowledge_chunks TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON support_knowledge_chunks TO app_user;

COMMENT ON TABLE support_knowledge_chunks IS
    'Embedded manuals / runbooks for role-aware support chat RAG';
