-- Hybrid RAG: English full-text search over support knowledge bodies (BM25-style via tsvector).
ALTER TABLE support_knowledge_chunks
    ADD COLUMN IF NOT EXISTS body_tsv tsvector
        GENERATED ALWAYS AS (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(body, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_support_knowledge_chunks_body_tsv
    ON support_knowledge_chunks USING GIN (body_tsv);
