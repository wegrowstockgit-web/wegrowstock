-- Hierarchical (parent-child) RAG + LLM-enriched JSONB metadata on support knowledge chunks.
-- Note: V094 already applied hybrid FTS (body_tsv); this is the hierarchical extension.

ALTER TABLE support_knowledge_chunks
    ADD COLUMN IF NOT EXISTS parent_chunk_id UUID
        REFERENCES support_knowledge_chunks (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS parent_content TEXT,
    ADD COLUMN IF NOT EXISTS context_summary TEXT,
    ADD COLUMN IF NOT EXISTS enriched_metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_support_knowledge_enriched_meta
    ON support_knowledge_chunks USING gin (enriched_metadata);

CREATE INDEX IF NOT EXISTS idx_support_knowledge_parent_chunk
    ON support_knowledge_chunks (parent_chunk_id);

COMMENT ON COLUMN support_knowledge_chunks.parent_chunk_id IS
    'Child → parent link for hierarchical RAG; NULL on parent (or legacy) rows';
COMMENT ON COLUMN support_knowledge_chunks.parent_content IS
    'Full parent chunk text returned to the LLM when a child vector hits';
COMMENT ON COLUMN support_knowledge_chunks.context_summary IS
    'Document-level 2-sentence summary prepended before child embedding';
COMMENT ON COLUMN support_knowledge_chunks.enriched_metadata IS
    'LLM-extracted module/roles/errorCode/resolutionLevel/entities JSON';
