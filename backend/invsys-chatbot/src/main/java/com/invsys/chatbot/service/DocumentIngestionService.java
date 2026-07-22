package com.invsys.chatbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invsys.support.SupportKnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Hierarchical RAG ETL: document summary → parent (~1200 tok) / child (~300 tok) chunks,
 * contextual child embeddings, and LLM-stamped {@code enriched_metadata}.
 */
@Service
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SupportKnowledgeRepository knowledgeRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectProvider<ChatModel> chatModel;

    private final TokenTextSplitter parentSplitter = TokenTextSplitter.builder()
            .withChunkSize(1200)
            .withMinChunkSizeChars(80)
            .withMinChunkLengthToEmbed(20)
            .withMaxNumChunks(64)
            .withKeepSeparator(true)
            .build();

    private final TokenTextSplitter childSplitter = TokenTextSplitter.builder()
            .withChunkSize(300)
            .withMinChunkSizeChars(40)
            .withMinChunkLengthToEmbed(12)
            .withMaxNumChunks(32)
            .withKeepSeparator(true)
            .build();

    public DocumentIngestionService(
            SupportKnowledgeRepository knowledgeRepository,
            EmbeddingModel embeddingModel,
            ObjectProvider<ChatModel> chatModel
    ) {
        this.knowledgeRepository = knowledgeRepository;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    public IngestResult ingest(IngestRequest request) {
        String title = StringUtils.hasText(request.title()) ? request.title().trim() : "Untitled SOP";
        String source = StringUtils.hasText(request.sourcePath()) ? request.sourcePath().trim() : "upload";
        String body = request.content() == null ? "" : request.content().trim();
        if (body.isBlank()) {
            throw new IllegalArgumentException("Document content is required");
        }

        List<String> roles = normalizeRoles(request.audienceRoles());
        List<String> routes = normalizeRoutes(request.routeHints());
        // Stable frontmatter slugs stay idempotent; free-text titles still get a short hash suffix.
        String baseSlug = StringUtils.hasText(request.slug())
                ? slugifyStable(request.slug().trim())
                : slugify(title);
        String contentSha = StringUtils.hasText(request.contentSha()) ? request.contentSha().trim() : null;
        boolean llmEnrichment = request.llmEnrichment();

        String contextSummary = llmEnrichment
                ? summarizeDocument(title, body)
                : heuristicSummary(title, body);
        List<Document> parents = parentSplitter.apply(List.of(new Document(body)));
        if (parents.isEmpty()) {
            parents = List.of(new Document(body));
        }

        int childCount = 0;
        int parentIndex = 0;
        for (Document parentDoc : parents) {
            String parentText = parentDoc.getText() == null ? "" : parentDoc.getText().strip();
            if (parentText.isBlank()) {
                parentIndex++;
                continue;
            }
            String parentSlug = parents.size() == 1 ? baseSlug + "-p0" : baseSlug + "-p" + parentIndex;
            Map<String, Object> parentMeta = tierMetadata(
                    "PARENT", title, source, parentSlug, roles, routes, contextSummary, null, contentSha);
            float[] parentEmbedding = embeddingModel.embed(
                    "Document Summary: " + contextSummary + "\n\nParent Section: " + parentText);
            UUID parentId = knowledgeRepository.upsertHierarchical(
                    parentSlug,
                    title + " (parent " + parentIndex + ")",
                    parentText,
                    roles,
                    routes,
                    source,
                    parentEmbedding,
                    null,
                    parentText,
                    contextSummary,
                    toJson(parentMeta));

            List<Document> children = childSplitter.apply(List.of(new Document(parentText)));
            if (children.isEmpty()) {
                children = List.of(new Document(parentText));
            }
            int childIndex = 0;
            for (Document childDoc : children) {
                String childText = childDoc.getText() == null ? "" : childDoc.getText().strip();
                if (childText.isBlank()) {
                    childIndex++;
                    continue;
                }
                String childSlug = parentSlug + "-c" + childIndex;
                ChunkMetadataExtraction extracted = llmEnrichment
                        ? extractMetadata(title, childText, roles)
                        : heuristicMetadata(childText, roles);
                Map<String, Object> childMeta = tierMetadata(
                        "CHILD", title, source, childSlug, roles, routes, contextSummary, extracted, contentSha);
                String embedText = "Document Summary: " + contextSummary + "\n\nChild Section: " + childText;
                float[] childEmbedding = embeddingModel.embed(embedText);
                knowledgeRepository.upsertHierarchical(
                        childSlug,
                        title + " §" + parentIndex + "." + childIndex,
                        childText,
                        mergeRoles(roles, extracted.targetRoles()),
                        routes,
                        source,
                        childEmbedding,
                        parentId,
                        parentText,
                        contextSummary,
                        toJson(childMeta));
                childCount++;
                childIndex++;
            }
            parentIndex++;
        }

        log.info("Hierarchical ingest baseSlug={} parents={} children={}", baseSlug, parentIndex, childCount);
        return new IngestResult(baseSlug, childCount, roles, routes, contextSummary);
    }

    String summarizeDocument(String title, String body) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return heuristicSummary(title, body);
        }
        try {
            String summary = ChatClient.create(model)
                    .prompt()
                    .system("""
                            Summarize this warehouse SOP in exactly 2 concise sentences for retrieval context. \
                            No markdown. No bullet lists.
                            """)
                    .user("Title: " + title + "\n\n" + truncate(body, 6000))
                    .call()
                    .content();
            if (summary != null && !summary.isBlank()) {
                return summary.trim();
            }
        } catch (RuntimeException ex) {
            log.warn("Document summary LLM failed; using heuristic: {}", ex.toString());
        }
        return heuristicSummary(title, body);
    }

    ChunkMetadataExtraction extractMetadata(String title, String childText, List<String> fallbackRoles) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return heuristicMetadata(childText, fallbackRoles);
        }
        try {
            ChunkMetadataExtraction extracted = ChatClient.create(model)
                    .prompt()
                    .system("""
                            Extract structured warehouse support metadata as JSON matching fields: \
                            module (PURCHASING|FULFILLMENT|INVENTORY|FINTECH|SHOWROOM|NONE), \
                            targetRoles (array of PICKER|WAREHOUSE_MANAGER|ADMIN|OWNER|B2B_CUSTOMER), \
                            errorCode (e.g. 409_CONFLICT, CREDIT_HOLD, ALLOCATION_LOCKED, or NONE), \
                            resolutionLevel (OPERATOR_SELF_SERVICE|MANAGER_REVIEW|ADMIN_OVERRIDE), \
                            entitiesMentioned (array from SKU,LPN,PURCHASE_ORDER,SALES_ORDER,BIN,WAVE,LOT). \
                            Reply with JSON only.
                            """)
                    .user("Title: " + title + "\n\nSection:\n" + truncate(childText, 2500))
                    .call()
                    .entity(ChunkMetadataExtraction.class);
            if (extracted != null) {
                return normalizeExtraction(extracted, fallbackRoles);
            }
        } catch (RuntimeException ex) {
            log.debug("Metadata extraction LLM failed; heuristic fallback: {}", ex.toString());
        }
        return heuristicMetadata(childText, fallbackRoles);
    }

    private static ChunkMetadataExtraction normalizeExtraction(
            ChunkMetadataExtraction raw,
            List<String> fallbackRoles
    ) {
        String module = raw.module() == null || raw.module().isBlank() ? "NONE" : raw.module().trim().toUpperCase(Locale.ROOT);
        String error = raw.errorCode() == null || raw.errorCode().isBlank() ? "NONE" : raw.errorCode().trim().toUpperCase(Locale.ROOT);
        String level = raw.resolutionLevel() == null || raw.resolutionLevel().isBlank()
                ? "OPERATOR_SELF_SERVICE"
                : raw.resolutionLevel().trim().toUpperCase(Locale.ROOT);
        List<String> roles = raw.targetRoles() == null || raw.targetRoles().isEmpty()
                ? fallbackRoles
                : normalizeRoles(raw.targetRoles());
        List<String> entities = raw.entitiesMentioned() == null
                ? List.of()
                : raw.entitiesMentioned().stream()
                        .filter(StringUtils::hasText)
                        .map(v -> v.trim().toUpperCase(Locale.ROOT))
                        .distinct()
                        .toList();
        return new ChunkMetadataExtraction(module, roles, error, level, entities);
    }

    public static ChunkMetadataExtraction heuristicMetadata(String text, List<String> fallbackRoles) {
        String q = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String module = "NONE";
        // Prefer fulfillment/inventory signals over incidental "purchase order" mentions.
        if (q.contains("allocat") || q.contains("wave") || q.contains("pick") || q.contains("fulfill")
                || q.contains("sales order") || q.contains("conflict panel")) {
            module = "FULFILLMENT";
        } else if (q.contains("cycle count") || q.contains("lot") || q.contains("bin") || q.contains("inventory")) {
            module = "INVENTORY";
        } else if (q.contains("purchase") || q.contains("inbound") || q.contains("asn")) {
            module = "PURCHASING";
        } else if (q.contains("showroom") || q.contains("b2b") || q.contains("cart")) {
            module = "SHOWROOM";
        } else if (q.contains("credit") || q.contains("invoice") || q.contains("fintech")) {
            module = "FINTECH";
        }
        String error = "NONE";
        if (q.contains("409") || q.contains("conflict")) {
            error = "409_CONFLICT";
        } else if (q.contains("credit hold")) {
            error = "CREDIT_HOLD";
        } else if (q.contains("allocat") && q.contains("lock")) {
            error = "ALLOCATION_LOCKED";
        }
        String level = "OPERATOR_SELF_SERVICE";
        if (q.contains("admin") || q.contains("override")) {
            level = "ADMIN_OVERRIDE";
        } else if (q.contains("manager") || q.contains("approve")) {
            level = "MANAGER_REVIEW";
        }
        List<String> entities = new ArrayList<>();
        if (q.contains("sku")) {
            entities.add("SKU");
        }
        if (q.contains("lpn")) {
            entities.add("LPN");
        }
        if (q.contains("purchase order") || q.contains(" po ")) {
            entities.add("PURCHASE_ORDER");
        }
        if (q.contains("sales order") || q.contains(" so ")) {
            entities.add("SALES_ORDER");
        }
        if (q.contains("bin")) {
            entities.add("BIN");
        }
        if (q.contains("wave")) {
            entities.add("WAVE");
        }
        if (q.contains("lot")) {
            entities.add("LOT");
        }
        return new ChunkMetadataExtraction(module, fallbackRoles == null ? List.of() : fallbackRoles, error, level, entities);
    }

    private static String heuristicSummary(String title, String body) {
        String first = body.lines().map(String::strip).filter(s -> !s.isBlank()).findFirst().orElse(title);
        if (first.length() > 180) {
            first = first.substring(0, 177) + "…";
        }
        return "Operations playbook for " + title + ". " + first;
    }

    private static Map<String, Object> tierMetadata(
            String tier,
            String title,
            String source,
            String slug,
            List<String> roles,
            List<String> routes,
            String contextSummary,
            ChunkMetadataExtraction extracted,
            String contentSha
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("chunkTier", tier);
        meta.put("title", title);
        meta.put("slug", slug);
        meta.put("source_path", source);
        meta.put("audience_roles", roles);
        meta.put("route_hints", routes);
        meta.put("context_summary", contextSummary);
        if (StringUtils.hasText(contentSha)) {
            meta.put("contentSha", contentSha);
        }
        if (extracted != null) {
            meta.put("module", extracted.module());
            meta.put("targetRoles", extracted.targetRoles());
            meta.put("errorCode", extracted.errorCode());
            meta.put("resolutionLevel", extracted.resolutionLevel());
            meta.put("entitiesMentioned", extracted.entitiesMentioned());
        }
        return meta;
    }

    private static List<String> mergeRoles(List<String> base, List<String> extracted) {
        LinkedHashMap<String, Boolean> out = new LinkedHashMap<>();
        if (base != null) {
            for (String r : base) {
                out.put(r, Boolean.TRUE);
            }
        }
        if (extracted != null) {
            for (String r : extracted) {
                out.put(r, Boolean.TRUE);
            }
        }
        return List.copyOf(out.keySet());
    }

    private static String toJson(Map<String, Object> meta) {
        try {
            return MAPPER.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static List<String> normalizeRoles(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static List<String> normalizeRoutes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** Keep explicit SOP slugs stable across restarts (no volatile hash suffix). */
    private static String slugifyStable(String raw) {
        String base = raw.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "sop";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        return base;
    }

    private static String slugify(String raw) {
        String base = slugifyStable(raw);
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
            List<String> routeHints,
            String contentSha,
            boolean llmEnrichment
    ) {
        public IngestRequest(
                String title,
                String slug,
                String content,
                String sourcePath,
                List<String> audienceRoles,
                List<String> routeHints
        ) {
            this(title, slug, content, sourcePath, audienceRoles, routeHints, null, true);
        }

        public IngestRequest(
                String title,
                String slug,
                String content,
                String sourcePath,
                List<String> audienceRoles,
                List<String> routeHints,
                String contentSha
        ) {
            this(title, slug, content, sourcePath, audienceRoles, routeHints, contentSha, true);
        }
    }

    public record IngestResult(
            String baseSlug,
            int chunkCount,
            List<String> audienceRoles,
            List<String> routeHints,
            String contextSummary
    ) {
        public IngestResult(String baseSlug, int chunkCount, List<String> audienceRoles, List<String> routeHints) {
            this(baseSlug, chunkCount, audienceRoles, routeHints, null);
        }
    }
}
