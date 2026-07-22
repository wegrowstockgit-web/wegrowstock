package com.invsys.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
public class SupportKnowledgeRepository {

    private final JdbcTemplate jdbcTemplate;

    public SupportKnowledgeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsert(
            String slug,
            String title,
            String body,
            List<String> audienceRoles,
            List<String> routeHints,
            String sourcePath,
            float[] embedding
    ) {
        upsertHierarchical(
                slug, title, body, audienceRoles, routeHints, sourcePath, embedding,
                null, null, null, toMetadataJson(slug, title, audienceRoles, routeHints, sourcePath));
    }

    /**
     * Upsert a hierarchical parent or child chunk; returns the row id.
     */
    public UUID upsertHierarchical(
            String slug,
            String title,
            String body,
            List<String> audienceRoles,
            List<String> routeHints,
            String sourcePath,
            float[] embedding,
            UUID parentChunkId,
            String parentContent,
            String contextSummary,
            String enrichedMetadataJson
    ) {
        String metaJson = enrichedMetadataJson == null || enrichedMetadataJson.isBlank()
                ? toMetadataJson(slug, title, audienceRoles, routeHints, sourcePath)
                : enrichedMetadataJson;
        return jdbcTemplate.queryForObject("""
                INSERT INTO support_knowledge_chunks (
                    slug, title, body, content, metadata, audience_roles, route_hints, source_path,
                    embedding, parent_chunk_id, parent_content, context_summary, enriched_metadata, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?::json, ?::text[], ?::text[], ?,
                    ?::vector, ?, ?, ?, ?::jsonb, now()
                )
                ON CONFLICT (slug) DO UPDATE SET
                    title = EXCLUDED.title,
                    body = EXCLUDED.body,
                    content = EXCLUDED.content,
                    metadata = EXCLUDED.metadata,
                    audience_roles = EXCLUDED.audience_roles,
                    route_hints = EXCLUDED.route_hints,
                    source_path = EXCLUDED.source_path,
                    embedding = EXCLUDED.embedding,
                    parent_chunk_id = EXCLUDED.parent_chunk_id,
                    parent_content = EXCLUDED.parent_content,
                    context_summary = EXCLUDED.context_summary,
                    enriched_metadata = EXCLUDED.enriched_metadata,
                    updated_at = now()
                RETURNING id
                """,
                UUID.class,
                slug,
                title,
                body,
                body,
                metaJson,
                toPgArrayLiteral(audienceRoles),
                toPgArrayLiteral(routeHints),
                sourcePath,
                toVectorLiteral(embedding),
                parentChunkId,
                parentContent,
                contextSummary,
                metaJson);
    }

    private static String toMetadataJson(
            String slug,
            String title,
            List<String> audienceRoles,
            List<String> routeHints,
            String sourcePath
    ) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"slug\":\"").append(jsonEsc(slug)).append("\",");
        sb.append("\"title\":\"").append(jsonEsc(title)).append("\",");
        sb.append("\"source_path\":\"").append(jsonEsc(sourcePath == null ? "" : sourcePath)).append("\",");
        sb.append("\"audience_roles\":").append(toJsonStringArray(audienceRoles)).append(',');
        sb.append("\"route_hints\":").append(toJsonStringArray(routeHints));
        sb.append('}');
        return sb.toString();
    }

    private static String toJsonStringArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(jsonEsc(values.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonEsc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final int RRF_K = 60;

    /**
     * Dense-only cosine search (legacy entry point). Prefer {@link #searchHybrid} for RAG.
     */
    public List<SupportKnowledgeChunk> searchSimilar(
            float[] embedding,
            List<String> roles,
            String route,
            int topK
    ) {
        return searchHybrid(embedding, null, roles, route, topK);
    }

    /**
     * Hybrid retrieval: dense pgvector + English tsvector sparse search, fused with RRF (k=60).
     * Prefers child rows and returns {@code parent_content} for generation.
     */
    public List<SupportKnowledgeChunk> searchHybrid(
            float[] embedding,
            String queryText,
            List<String> roles,
            String route,
            int topK
    ) {
        int limit = Math.max(1, topK);
        int candidateLimit = Math.max(limit * 4, 12);
        String vector = toVectorLiteral(embedding);
        String rolesArray = toPgArrayLiteral(roles == null ? List.of() : roles);
        String routeNorm = route == null ? "" : route.trim();
        String textQuery = queryText == null ? "" : queryText.trim();
        String moduleHint = moduleHintForRoute(routeNorm);
        String resolutionHint = resolutionHintForRoles(roles);

        List<SupportKnowledgeChunk> dense = searchDense(
                vector, rolesArray, routeNorm, moduleHint, resolutionHint, candidateLimit);
        List<SupportKnowledgeChunk> sparse = textQuery.isBlank()
                ? List.of()
                : searchSparse(textQuery, rolesArray, routeNorm, moduleHint, resolutionHint, candidateLimit);
        return assembleParentContext(reciprocalRankFusion(dense, sparse, limit));
    }

    /**
     * Collapse duplicate parents and ensure {@link SupportKnowledgeChunk#promptBody()} is rich.
     */
    public static List<SupportKnowledgeChunk> assembleParentContext(List<SupportKnowledgeChunk> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashMap<String, SupportKnowledgeChunk> distinct = new java.util.LinkedHashMap<>();
        for (SupportKnowledgeChunk hit : hits) {
            String key = hit.parentChunkId() != null
                    ? "p:" + hit.parentChunkId()
                    : "c:" + hit.id();
            distinct.putIfAbsent(key, hit);
        }
        return List.copyOf(distinct.values());
    }

    private List<SupportKnowledgeChunk> searchDense(
            String vector,
            String rolesArray,
            String routeNorm,
            String moduleHint,
            String resolutionHint,
            int limit
    ) {
        if (!routeNorm.isBlank()) {
            List<SupportKnowledgeChunk> pinned = jdbcTemplate.query("""
                    SELECT id, slug, title, body, audience_roles, route_hints, source_path,
                           parent_chunk_id, parent_content, context_summary, enriched_metadata::text AS enriched_metadata,
                           (1 - (embedding <=> ?::vector)) AS score
                      FROM support_knowledge_chunks
                     WHERE (cardinality(audience_roles) = 0 OR audience_roles && ?::text[])
                       AND (parent_chunk_id IS NOT NULL OR enriched_metadata->>'chunkTier' IS DISTINCT FROM 'PARENT')
                       AND cardinality(route_hints) > 0
                       AND EXISTS (
                            SELECT 1 FROM unnest(route_hints) h
                             WHERE ? LIKE ('%' || h || '%')
                                OR h LIKE ('%' || split_part(?, '?', 1) || '%')
                          )
                       AND (
                            enriched_metadata = '{}'::jsonb
                            OR enriched_metadata->>'module' IS NULL
                            OR enriched_metadata->>'module' IN ('NONE', ?)
                            OR ? = ''
                           )
                       AND (
                            enriched_metadata = '{}'::jsonb
                            OR enriched_metadata->>'resolutionLevel' IS NULL
                            OR enriched_metadata->>'resolutionLevel' = ?
                            OR ? = ''
                           )
                     ORDER BY embedding <=> ?::vector
                     LIMIT ?
                    """,
                    (rs, rowNum) -> mapRow(rs),
                    vector,
                    rolesArray,
                    routeNorm,
                    routeNorm,
                    moduleHint,
                    moduleHint,
                    resolutionHint,
                    resolutionHint,
                    vector,
                    limit);
            if (pinned.size() >= Math.min(2, Math.max(1, limit / 4))) {
                return pinned;
            }
        }

        return jdbcTemplate.query("""
                SELECT id, slug, title, body, audience_roles, route_hints, source_path,
                       parent_chunk_id, parent_content, context_summary, enriched_metadata::text AS enriched_metadata,
                       (1 - (embedding <=> ?::vector)) AS score
                  FROM support_knowledge_chunks
                 WHERE (cardinality(audience_roles) = 0 OR audience_roles && ?::text[])
                   AND (parent_chunk_id IS NOT NULL OR enriched_metadata->>'chunkTier' IS DISTINCT FROM 'PARENT')
                   AND (
                        enriched_metadata = '{}'::jsonb
                        OR enriched_metadata->>'module' IS NULL
                        OR enriched_metadata->>'module' IN ('NONE', ?)
                        OR ? = ''
                       )
                   AND (
                        enriched_metadata = '{}'::jsonb
                        OR enriched_metadata->>'resolutionLevel' IS NULL
                        OR enriched_metadata->>'resolutionLevel' = ?
                        OR ? = ''
                       )
                 ORDER BY
                   CASE
                     WHEN cardinality(route_hints) > 0
                      AND EXISTS (
                            SELECT 1 FROM unnest(route_hints) h
                             WHERE ? LIKE ('%' || h || '%')
                          ) THEN 0
                     ELSE 1
                   END,
                   (embedding <=> ?::vector)
                     * CASE
                         WHEN cardinality(route_hints) > 0
                          AND EXISTS (
                                SELECT 1 FROM unnest(route_hints) h
                                 WHERE ? LIKE ('%' || h || '%')
                              ) THEN 0.55
                         ELSE 1.0
                       END
                 LIMIT ?
                """,
                (rs, rowNum) -> mapRow(rs),
                vector,
                rolesArray,
                moduleHint,
                moduleHint,
                resolutionHint,
                resolutionHint,
                routeNorm,
                vector,
                routeNorm,
                limit);
    }

    private List<SupportKnowledgeChunk> searchSparse(
            String queryText,
            String rolesArray,
            String routeNorm,
            String moduleHint,
            String resolutionHint,
            int limit
    ) {
        try {
            return jdbcTemplate.query("""
                    SELECT id, slug, title, body, audience_roles, route_hints, source_path,
                           parent_chunk_id, parent_content, context_summary, enriched_metadata::text AS enriched_metadata,
                           ts_rank_cd(body_tsv, websearch_to_tsquery('english', ?)) AS score
                      FROM support_knowledge_chunks
                     WHERE body_tsv @@ websearch_to_tsquery('english', ?)
                       AND (cardinality(audience_roles) = 0 OR audience_roles && ?::text[])
                       AND (parent_chunk_id IS NOT NULL OR enriched_metadata->>'chunkTier' IS DISTINCT FROM 'PARENT')
                       AND (
                            enriched_metadata = '{}'::jsonb
                            OR enriched_metadata->>'module' IS NULL
                            OR enriched_metadata->>'module' IN ('NONE', ?)
                            OR ? = ''
                           )
                       AND (
                            enriched_metadata = '{}'::jsonb
                            OR enriched_metadata->>'resolutionLevel' IS NULL
                            OR enriched_metadata->>'resolutionLevel' = ?
                            OR ? = ''
                           )
                     ORDER BY
                       CASE
                         WHEN cardinality(route_hints) > 0
                          AND EXISTS (
                                SELECT 1 FROM unnest(route_hints) h
                                 WHERE ? LIKE ('%' || h || '%')
                              ) THEN 0
                         ELSE 1
                       END,
                       score DESC
                     LIMIT ?
                    """,
                    (rs, rowNum) -> mapRow(rs),
                    queryText,
                    queryText,
                    rolesArray,
                    moduleHint,
                    moduleHint,
                    resolutionHint,
                    resolutionHint,
                    routeNorm,
                    limit);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    public static String moduleHintForRoute(String route) {
        if (route == null || route.isBlank()) {
            return "";
        }
        String r = route.toLowerCase(Locale.ROOT);
        if (r.contains("purchase") || r.contains("inbound") || r.contains("asn")) {
            return "PURCHASING";
        }
        if (r.contains("showroom")) {
            return "SHOWROOM";
        }
        if (r.contains("fintech") || r.contains("billing") || r.contains("credit")) {
            return "FINTECH";
        }
        if (r.contains("cycle-count") || r.contains("inventory") || r.contains("lot")) {
            return "INVENTORY";
        }
        if (r.contains("sales-order") || r.contains("fulfillment") || r.contains("wave") || r.contains("pick")) {
            return "FULFILLMENT";
        }
        return "";
    }

    public static String resolutionHintForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "";
        }
        boolean admin = roles.stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r) || "OWNER".equalsIgnoreCase(r));
        if (admin) {
            return "ADMIN_OVERRIDE";
        }
        boolean manager = roles.stream().anyMatch(r -> "WAREHOUSE_MANAGER".equalsIgnoreCase(r));
        if (manager) {
            return "MANAGER_REVIEW";
        }
        boolean pickerOnly = roles.size() == 1 && "PICKER".equalsIgnoreCase(roles.getFirst());
        if (pickerOnly) {
            return "OPERATOR_SELF_SERVICE";
        }
        return "";
    }

    /**
     * Reciprocal Rank Fusion: {@code score = Σ 1/(k + rank)} across ranked lists (1-based ranks).
     */
    public static List<SupportKnowledgeChunk> reciprocalRankFusion(
            List<SupportKnowledgeChunk> dense,
            List<SupportKnowledgeChunk> sparse,
            int topK
    ) {
        java.util.LinkedHashMap<UUID, SupportKnowledgeChunk> byId = new java.util.LinkedHashMap<>();
        java.util.Map<UUID, Double> scores = new java.util.HashMap<>();

        accumulateRrf(dense, byId, scores);
        accumulateRrf(sparse, byId, scores);

        return scores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, topK))
                .map(e -> {
                    SupportKnowledgeChunk c = byId.get(e.getKey());
                    return new SupportKnowledgeChunk(
                            c.id(), c.slug(), c.title(), c.body(),
                            c.audienceRoles(), c.routeHints(), c.sourcePath(),
                            e.getValue(),
                            c.parentChunkId(), c.parentContent(), c.contextSummary(), c.enrichedMetadataJson());
                })
                .toList();
    }

    private static void accumulateRrf(
            List<SupportKnowledgeChunk> ranked,
            java.util.Map<UUID, SupportKnowledgeChunk> byId,
            java.util.Map<UUID, Double> scores
    ) {
        if (ranked == null) {
            return;
        }
        for (int i = 0; i < ranked.size(); i++) {
            SupportKnowledgeChunk chunk = ranked.get(i);
            if (chunk == null || chunk.id() == null) {
                continue;
            }
            byId.putIfAbsent(chunk.id(), chunk);
            int rank = i + 1;
            scores.merge(chunk.id(), 1.0 / (RRF_K + rank), Double::sum);
        }
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_knowledge_chunks", Long.class);
        return count == null ? 0L : count;
    }

    /**
     * Idempotency helper for SOP directory ingestion: true when this source was already
     * ingested with the same content checksum.
     */
    public boolean hasSourceContentSha(String sourcePath, String contentSha) {
        if (sourcePath == null || sourcePath.isBlank() || contentSha == null || contentSha.isBlank()) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM support_knowledge_chunks
                     WHERE source_path = ?
                       AND (
                            enriched_metadata->>'contentSha' = ?
                            OR metadata->>'contentSha' = ?
                           )
                    """,
                    Integer.class,
                    sourcePath,
                    contentSha,
                    contentSha);
            return count != null && count > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public List<SupportKnowledgeChunk> findBySlugPrefix(String prefix) {
        return jdbcTemplate.query("""
                SELECT id, slug, title, body, audience_roles, route_hints, source_path,
                       parent_chunk_id, parent_content, context_summary, enriched_metadata::text AS enriched_metadata,
                       1.0 AS score
                  FROM support_knowledge_chunks
                 WHERE slug LIKE ?
                 ORDER BY slug
                """,
                (rs, rowNum) -> mapRow(rs),
                prefix + "%");
    }

    private static SupportKnowledgeChunk mapRow(ResultSet rs) throws SQLException {
        return new SupportKnowledgeChunk(
                (UUID) rs.getObject("id"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("body"),
                readTextArray(rs.getArray("audience_roles")),
                readTextArray(rs.getArray("route_hints")),
                rs.getString("source_path"),
                rs.getDouble("score"),
                (UUID) rs.getObject("parent_chunk_id"),
                rs.getString("parent_content"),
                rs.getString("context_summary"),
                rs.getString("enriched_metadata"));
    }

    private static List<String> readTextArray(Array array) throws SQLException {
        return readTextArrayPublic(array);
    }

    static List<String> readTextArrayPublic(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object raw = array.getArray();
        if (raw instanceof String[] strings) {
            return Arrays.asList(strings);
        }
        return List.of();
    }

    /** Postgres array literal: {"a","b"} */
    public static String toPgArrayLiteral(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(values.get(i).replace("\"", "\\\"")).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
