package com.invsys.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GraphRAG over PostgreSQL: seed by pgvector, expand with recursive CTE (2-hop).
 */
@Repository
public class SupportGraphRepository {

    private final JdbcTemplate jdbcTemplate;

    public SupportGraphRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertNode(String slug, String kind, String label, String chunkSlug) {
        jdbcTemplate.update("""
                INSERT INTO support_knowledge_nodes (slug, kind, label, chunk_slug)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (slug) DO UPDATE SET
                    kind = EXCLUDED.kind,
                    label = EXCLUDED.label,
                    chunk_slug = EXCLUDED.chunk_slug
                """,
                slug, kind, label, chunkSlug);
    }

    public void upsertEdge(String fromSlug, String toSlug, String relType) {
        jdbcTemplate.update("""
                INSERT INTO support_knowledge_edges (from_slug, to_slug, rel_type)
                VALUES (?, ?, ?)
                ON CONFLICT (from_slug, to_slug, rel_type) DO NOTHING
                """,
                fromSlug, toSlug, relType);
    }

    /**
     * 2-hop neighborhood of chunk slugs → distinct related chunk slugs (excluding seeds optional).
     */
    public List<String> expandChunkSlugs(List<String> seedChunkSlugs, int maxHops) {
        if (seedChunkSlugs == null || seedChunkSlugs.isEmpty()) {
            return List.of();
        }
        int hops = Math.max(1, Math.min(maxHops, 3));
        String seeds = SupportKnowledgeRepository.toPgArrayLiteral(seedChunkSlugs);
        return jdbcTemplate.queryForList("""
                WITH RECURSIVE seed_nodes AS (
                    SELECT n.slug
                      FROM support_knowledge_nodes n
                     WHERE n.chunk_slug = ANY(?::text[])
                ),
                walk AS (
                    SELECT sn.slug AS node_slug, 0 AS depth
                      FROM seed_nodes sn
                    UNION ALL
                    SELECT CASE
                             WHEN e.from_slug = w.node_slug THEN e.to_slug
                             ELSE e.from_slug
                           END,
                           w.depth + 1
                      FROM walk w
                      JOIN support_knowledge_edges e
                        ON e.from_slug = w.node_slug OR e.to_slug = w.node_slug
                     WHERE w.depth < ?
                )
                SELECT DISTINCT n.chunk_slug
                  FROM walk w
                  JOIN support_knowledge_nodes n ON n.slug = w.node_slug
                 WHERE n.chunk_slug IS NOT NULL
                   AND NOT (n.chunk_slug = ANY(?::text[]))
                """,
                String.class,
                seeds,
                hops,
                seeds);
    }

    public List<SupportKnowledgeChunk> loadChunksBySlug(List<String> chunkSlugs) {
        if (chunkSlugs == null || chunkSlugs.isEmpty()) {
            return List.of();
        }
        String arr = SupportKnowledgeRepository.toPgArrayLiteral(chunkSlugs);
        return jdbcTemplate.query("""
                SELECT id, slug, title, body, audience_roles, route_hints, source_path, 0.75 AS score
                  FROM support_knowledge_chunks
                 WHERE slug = ANY(?::text[])
                """,
                (rs, rowNum) -> new SupportKnowledgeChunk(
                        (UUID) rs.getObject("id"),
                        rs.getString("slug"),
                        rs.getString("title"),
                        rs.getString("body"),
                        SupportKnowledgeRepository.readTextArrayPublic(rs.getArray("audience_roles")),
                        SupportKnowledgeRepository.readTextArrayPublic(rs.getArray("route_hints")),
                        rs.getString("source_path"),
                        rs.getDouble("score")),
                arr);
    }

    /** Merge vector hits with 2-hop graph neighbors (seed first, then neighbors). */
    public List<SupportKnowledgeChunk> retrieveWithGraph(
            List<SupportKnowledgeChunk> vectorHits,
            int hopDepth
    ) {
        Map<String, SupportKnowledgeChunk> merged = new LinkedHashMap<>();
        List<String> seeds = new ArrayList<>();
        for (SupportKnowledgeChunk hit : vectorHits) {
            merged.put(hit.slug(), hit);
            seeds.add(hit.slug());
        }
        List<String> neighborSlugs = expandChunkSlugs(seeds, hopDepth);
        for (SupportKnowledgeChunk neighbor : loadChunksBySlug(neighborSlugs)) {
            merged.putIfAbsent(neighbor.slug(), neighbor);
        }
        return List.copyOf(merged.values());
    }

    public long nodeCount() {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_knowledge_nodes", Long.class);
        return n == null ? 0L : n;
    }
}
