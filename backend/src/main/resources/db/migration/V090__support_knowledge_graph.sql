-- GraphRAG: physical zones + digital flows linked to embedded doc chunks (PostgreSQL + recursive CTE).

CREATE TABLE support_knowledge_nodes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            TEXT NOT NULL UNIQUE,
    kind            TEXT NOT NULL,
    label           TEXT NOT NULL,
    chunk_slug      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_knowledge_nodes_kind_chk
        CHECK (kind IN ('ZONE', 'FLOW', 'DOC', 'ENTITY', 'ROLE'))
);

CREATE TABLE support_knowledge_edges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_slug       TEXT NOT NULL REFERENCES support_knowledge_nodes (slug) ON DELETE CASCADE,
    to_slug         TEXT NOT NULL REFERENCES support_knowledge_nodes (slug) ON DELETE CASCADE,
    rel_type        TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT support_knowledge_edges_uniq UNIQUE (from_slug, to_slug, rel_type)
);

CREATE INDEX idx_support_kg_edges_from ON support_knowledge_edges (from_slug);
CREATE INDEX idx_support_kg_edges_to ON support_knowledge_edges (to_slug);
CREATE INDEX idx_support_kg_nodes_chunk ON support_knowledge_nodes (chunk_slug);

GRANT SELECT, INSERT, UPDATE, DELETE ON support_knowledge_nodes TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON support_knowledge_nodes TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON support_knowledge_edges TO app_owner;
GRANT SELECT, INSERT, UPDATE, DELETE ON support_knowledge_edges TO app_user;

COMMENT ON TABLE support_knowledge_nodes IS
    'GraphRAG nodes: Dock/Racks zones, Procurement/Fulfillment flows, doc anchors';
COMMENT ON TABLE support_knowledge_edges IS
    'Typed relationships e.g. (PurchaseOrder)-[FULFILLS]->(SalesOrder), (Picker)-[SCANS]->(Bin)';
