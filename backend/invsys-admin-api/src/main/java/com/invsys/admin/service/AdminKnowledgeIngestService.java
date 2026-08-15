package com.invsys.admin.service;

import com.invsys.core.common.ApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Ingests platform markdown into {@code platform_knowledge_documents} and chunks into
 * {@code support_knowledge_chunks} with hash-based pseudo embeddings (768 dims to match V092).
 */
@Service
public class AdminKnowledgeIngestService {

    /** Matches {@code support_knowledge_chunks.embedding vector(768)} after V092. */
    static final int EMBEDDING_DIMS = 768;
    private static final int CHUNK_SIZE = 800;

    private final JdbcTemplate jdbc;

    public AdminKnowledgeIngestService(@Qualifier("bootstrapDataSource") DataSource bootstrapDataSource) {
        this.jdbc = new JdbcTemplate(bootstrapDataSource);
    }

    @Transactional
    public KnowledgeDocumentView ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "Markdown file required");
        }
        String original = file.getOriginalFilename() == null ? "document.md" : file.getOriginalFilename();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".md")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "Only .md files are supported");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "READ_FAILED", "Could not read upload");
        }

        String title = original.replaceAll("(?i)\\.md$", "");
        String slug = slugify(title) + "-" + UUID.randomUUID().toString().substring(0, 8);
        UUID docId = UUID.randomUUID();
        UUID createdBy = currentAdminId();

        List<String> chunks = chunkText(content, CHUNK_SIZE);
        jdbc.update("""
                INSERT INTO platform_knowledge_documents (id, title, slug, content_md, chunk_count, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, NOW(), ?)
                """,
                docId, title, slug, content, chunks.size(), createdBy);

        for (int i = 0; i < chunks.size(); i++) {
            String body = chunks.get(i);
            String chunkSlug = slug + "-c" + i;
            float[] embedding = hashEmbedding(body);
            String vectorLiteral = toVectorLiteral(embedding);
            String meta = "{\"source\":\"platform_knowledge\",\"documentId\":\"" + docId + "\",\"chunk\":" + i + "}";
            jdbc.update("""
                    INSERT INTO support_knowledge_chunks (
                        slug, title, body, content, metadata, audience_roles, route_hints, source_path,
                        embedding, updated_at
                    ) VALUES (
                        ?, ?, ?, ?, ?::json, ARRAY['SUPER_ADMIN']::text[], ARRAY[]::text[], ?,
                        ?::vector, NOW()
                    )
                    ON CONFLICT (slug) DO UPDATE SET
                        title = EXCLUDED.title,
                        body = EXCLUDED.body,
                        content = EXCLUDED.content,
                        metadata = EXCLUDED.metadata,
                        embedding = EXCLUDED.embedding,
                        updated_at = NOW()
                    """,
                    chunkSlug,
                    title + " [" + i + "]",
                    body,
                    body,
                    meta,
                    "platform://" + slug,
                    vectorLiteral);
        }

        return new KnowledgeDocumentView(docId, title, slug, chunks.size(), Instant.now());
    }

    public List<KnowledgeDocumentView> listDocuments() {
        return jdbc.query(
                """
                SELECT id, title, slug, chunk_count, created_at
                FROM platform_knowledge_documents
                ORDER BY created_at DESC
                """,
                (rs, rowNum) -> new KnowledgeDocumentView(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("title"),
                        rs.getString("slug"),
                        rs.getInt("chunk_count"),
                        rs.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public void deleteDocument(UUID id) {
        String slug = jdbc.query(
                "SELECT slug FROM platform_knowledge_documents WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                id);
        if (slug == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Knowledge document not found");
        }
        jdbc.update("DELETE FROM support_knowledge_chunks WHERE slug LIKE ?", slug + "-c%");
        jdbc.update("DELETE FROM platform_knowledge_documents WHERE id = ?", id);
    }

    static List<String> chunkText(String content, int size) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }
        String text = content.trim();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(i + size, text.length())));
        }
        return chunks;
    }

    /** Deterministic pseudo-embedding mapped to [-1, 1] for uniqueness without an external model. */
    static float[] hashEmbedding(String text) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(
                    (text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        float[] vector = new float[EMBEDDING_DIMS];
        for (int i = 0; i < EMBEDDING_DIMS; i++) {
            int b = digest[i % digest.length] & 0xff;
            vector[i] = ((b / 255f) * 2f - 1f) * (1f + (i % 17) / 100f);
        }
        return vector;
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

    private static String slugify(String title) {
        String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("^-+|-+$", "");
        return s.isBlank() ? "doc" : s;
    }

    private static UUID currentAdminId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    public record KnowledgeDocumentView(
            UUID id,
            String title,
            String slug,
            int chunkCount,
            Instant createdAt
    ) {
    }
}
