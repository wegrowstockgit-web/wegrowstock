package com.invsys.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
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
        jdbcTemplate.update("""
                INSERT INTO support_knowledge_chunks (
                    slug, title, body, content, metadata, audience_roles, route_hints, source_path, embedding, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?::json, ?::text[], ?::text[], ?, ?::vector, now()
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
                    updated_at = now()
                """,
                slug,
                title,
                body,
                body,
                toMetadataJson(slug, title, audienceRoles, routeHints, sourcePath),
                toPgArrayLiteral(audienceRoles),
                toPgArrayLiteral(routeHints),
                sourcePath,
                toVectorLiteral(embedding));
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

    public List<SupportKnowledgeChunk> searchSimilar(
            float[] embedding,
            List<String> roles,
            String route,
            int topK
    ) {
        String vector = toVectorLiteral(embedding);
        String rolesArray = toPgArrayLiteral(roles == null ? List.of() : roles);
        String routeNorm = route == null ? "" : route.trim();

        // Pin to active route when possible so Sales Orders "allocation" does not pull manufacturing docs.
        if (!routeNorm.isBlank()) {
            List<SupportKnowledgeChunk> pinned = jdbcTemplate.query("""
                    SELECT id, slug, title, body, audience_roles, route_hints, source_path,
                           (1 - (embedding <=> ?::vector)) AS score
                      FROM support_knowledge_chunks
                     WHERE (cardinality(audience_roles) = 0 OR audience_roles && ?::text[])
                       AND cardinality(route_hints) > 0
                       AND EXISTS (
                            SELECT 1 FROM unnest(route_hints) h
                             WHERE ? LIKE ('%' || h || '%')
                                OR h LIKE ('%' || split_part(?, '?', 1) || '%')
                          )
                     ORDER BY embedding <=> ?::vector
                     LIMIT ?
                    """,
                    (rs, rowNum) -> mapRow(rs),
                    vector,
                    rolesArray,
                    routeNorm,
                    routeNorm,
                    vector,
                    topK);
            if (pinned.size() >= Math.min(2, Math.max(1, topK / 2))) {
                return pinned;
            }
        }

        return jdbcTemplate.query("""
                SELECT id, slug, title, body, audience_roles, route_hints, source_path,
                       (1 - (embedding <=> ?::vector)) AS score
                  FROM support_knowledge_chunks
                 WHERE cardinality(audience_roles) = 0
                    OR audience_roles && ?::text[]
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
                routeNorm,
                vector,
                routeNorm,
                topK);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM support_knowledge_chunks", Long.class);
        return count == null ? 0L : count;
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
                rs.getDouble("score"));
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
