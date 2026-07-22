package com.invsys.chatbot.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin ETL: chunk Markdown/PDF-extracted text, embed via {@link VectorStore#add},
 * and stamp {@code audience_roles} / {@code route_hints} into document metadata.
 */
@Service
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = new TokenTextSplitter();

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public IngestResult ingest(IngestRequest request) {
        String title = StringUtils.hasText(request.title()) ? request.title().trim() : "Untitled SOP";
        String source = StringUtils.hasText(request.sourcePath()) ? request.sourcePath().trim() : "upload";
        String body = request.content() == null ? "" : request.content().trim();
        if (body.isBlank()) {
            throw new IllegalArgumentException("Document content is required");
        }

        List<String> roles = normalizeList(request.audienceRoles());
        List<String> routes = normalizeList(request.routeHints());
        String baseSlug = slugify(StringUtils.hasText(request.slug()) ? request.slug() : title);

        Document root = new Document(body, baseMetadata(title, source, baseSlug, roles, routes));
        List<Document> chunks = splitter.apply(List.of(root));
        List<Document> stamped = new ArrayList<>(chunks.size());
        int index = 0;
        for (Document chunk : chunks) {
            String chunkSlug = chunks.size() == 1 ? baseSlug : baseSlug + "-c" + index;
            Map<String, Object> meta = new LinkedHashMap<>(chunk.getMetadata());
            meta.putAll(baseMetadata(title, source, chunkSlug, roles, routes));
            meta.put("chunkIndex", index);
            stamped.add(new Document(UUID.nameUUIDFromBytes(chunkSlug.getBytes(StandardCharsets.UTF_8)).toString(),
                    chunk.getText(),
                    meta));
            index++;
        }
        vectorStore.add(stamped);
        return new IngestResult(baseSlug, stamped.size(), roles, routes);
    }

    private static Map<String, Object> baseMetadata(
            String title,
            String source,
            String slug,
            List<String> roles,
            List<String> routes
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title);
        meta.put("slug", slug);
        meta.put("source_path", source);
        meta.put("audience_roles", roles);
        meta.put("route_hints", routes);
        return meta;
    }

    private static List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static String slugify(String raw) {
        String base = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "sop";
        }
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        return base + "-" + shortHash(raw);
    }

    private static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record IngestRequest(
            String title,
            String slug,
            String content,
            String sourcePath,
            List<String> audienceRoles,
            List<String> routeHints
    ) {
    }

    public record IngestResult(
            String baseSlug,
            int chunkCount,
            List<String> audienceRoles,
            List<String> routeHints
    ) {
    }
}
