package com.invsys.chatbot.config;

import com.invsys.chatbot.config.SopFrontmatterParser.ParsedDocument;
import com.invsys.chatbot.service.DocumentIngestionService;
import com.invsys.chatbot.service.DocumentIngestionService.IngestRequest;
import com.invsys.chatbot.service.DocumentIngestionService.IngestResult;
import com.invsys.support.SupportGraphRepository;
import com.invsys.support.SupportKnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

/**
 * On boot, scans {@code docs/sops/*.md} (filesystem or classpath), parses frontmatter,
 * and ingests manuals through {@link DocumentIngestionService}. Order 42 runs after the
 * built-in knowledge/graph seeds (40/41).
 * <p>
 * Work is scheduled asynchronously so Docker healthchecks are not blocked by embedding /
 * hierarchical chunking of large SOP manuals.
 */
@Component
@Order(42)
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "invsys.support.sop.directory-ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class SopDirectoryIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SopDirectoryIngestionRunner.class);
    private static final String CLASSPATH_PATTERN = "classpath*:docs/sops/*.md";

    private final DocumentIngestionService documentIngestionService;
    private final SupportKnowledgeRepository knowledgeRepository;
    private final SupportGraphRepository graphRepository;
    private final ExecutorService virtualThreadExecutor;

    public SopDirectoryIngestionRunner(
            DocumentIngestionService documentIngestionService,
            SupportKnowledgeRepository knowledgeRepository,
            SupportGraphRepository graphRepository,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor
    ) {
        this.documentIngestionService = documentIngestionService;
        this.knowledgeRepository = knowledgeRepository;
        this.graphRepository = graphRepository;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Never block readiness / health on multi-minute SOP embedding work.
        virtualThreadExecutor.execute(() -> {
            try {
                ingestAll();
            } catch (Exception ex) {
                log.warn("[SOP Ingestion] Directory scan aborted without blocking startup: {}", ex.toString());
            }
        });
        log.info("[SOP Ingestion] Scheduled async docs/sops scan (non-blocking)");
    }

    /** Package/tests and ITs may call this synchronously; boot uses {@link #run} (async). */
    public void ingestAll() throws IOException {
        List<SopSource> sources = discoverSources();
        if (sources.isEmpty()) {
            log.info("[SOP Ingestion] No SOP manuals found under docs/sops/ (filesystem or classpath); skipping");
            return;
        }
        int ingested = 0;
        int skipped = 0;
        int failed = 0;
        for (SopSource source : sources) {
            try {
                Outcome outcome = ingestOne(source);
                if (outcome == Outcome.INGESTED) {
                    ingested++;
                } else if (outcome == Outcome.SKIPPED) {
                    skipped++;
                }
            } catch (Exception ex) {
                failed++;
                log.warn("[SOP Ingestion] Failed to ingest {}: {}", source.label(), ex.toString());
            }
        }
        log.info(
                "[SOP Ingestion] Auto-ingested {} SOP manuals into vector store (skipped={}, failed={}, discovered={})",
                ingested,
                skipped,
                failed,
                sources.size());
    }

    private Outcome ingestOne(SopSource source) throws IOException {
        String markdown = source.readUtf8();
        String contentSha = sha256Hex(markdown);
        ParsedDocument parsed = SopFrontmatterParser.parse(markdown);
        SopFrontmatter fm = parsed.frontmatter();
        String sourcePath = StringUtils.hasText(fm.sourcePath())
                ? fm.sourcePath()
                : "docs/sops/" + source.fileName();
        if (knowledgeRepository.hasSourceContentSha(sourcePath, contentSha)) {
            log.debug("[SOP Ingestion] Unchanged checksum; skipping {}", sourcePath);
            return Outcome.SKIPPED;
        }
        if (parsed.body().isBlank()) {
            log.warn("[SOP Ingestion] Empty body after frontmatter; skipping {}", source.label());
            return Outcome.SKIPPED;
        }
        // Heuristic enrichment on boot — avoid N Gemini chat calls that stall deploy healthchecks.
        IngestResult result = documentIngestionService.ingest(new IngestRequest(
                fm.title(),
                fm.slug(),
                parsed.body(),
                sourcePath,
                fm.audienceRoles(),
                fm.routeHints(),
                contentSha,
                false));
        String nodeSlug = "doc-" + result.baseSlug();
        String chunkSlug = result.baseSlug() + "-p0";
        graphRepository.upsertNode(nodeSlug, "DOC", fm.title(), chunkSlug);
        log.info(
                "[SOP Ingestion] Ingested {} children={} node={} source={}",
                result.baseSlug(),
                result.chunkCount(),
                nodeSlug,
                sourcePath);
        return Outcome.INGESTED;
    }

    private List<SopSource> discoverSources() throws IOException {
        Map<String, SopSource> byName = new LinkedHashMap<>();
        Path dir = resolveFilesystemSopDirectory();
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> byName.put(p.getFileName().toString(), SopSource.filesystem(p)));
            }
            log.info("[SOP Ingestion] Using filesystem directory {}", dir.toAbsolutePath());
        }
        if (byName.isEmpty()) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(CLASSPATH_PATTERN);
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(r -> {
                try {
                    return r.getFilename() == null ? "" : r.getFilename();
                } catch (Exception e) {
                    return "";
                }
            }));
            for (Resource resource : sorted) {
                if (resource == null || !resource.exists()) {
                    continue;
                }
                String name = resource.getFilename();
                if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    continue;
                }
                byName.putIfAbsent(name, SopSource.classpath(resource, name));
            }
            if (!byName.isEmpty()) {
                log.info("[SOP Ingestion] Using classpath {} ({} files)", CLASSPATH_PATTERN, byName.size());
            }
        }
        return List.copyOf(byName.values());
    }

    static Path resolveFilesystemSopDirectory() {
        String override = System.getProperty("invsys.support.sop.directory");
        if (!StringUtils.hasText(override)) {
            override = System.getenv("INVSYS_SOP_DIRECTORY");
        }
        if (StringUtils.hasText(override)) {
            Path p = Path.of(override.trim());
            return Files.isDirectory(p) ? p : null;
        }
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path direct = cwd.resolve("docs").resolve("sops");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path cursor = cwd;
        for (int i = 0; i < 6; i++) {
            Path candidate = cursor.resolve("docs").resolve("sops");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path parent = cursor.getParent();
            if (parent == null || parent.equals(cursor)) {
                break;
            }
            cursor = parent;
        }
        return null;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private enum Outcome {
        INGESTED,
        SKIPPED
    }

    private record SopSource(String fileName, Path path, Resource resource) {
        static SopSource filesystem(Path path) {
            return new SopSource(path.getFileName().toString(), path, null);
        }

        static SopSource classpath(Resource resource, String fileName) {
            return new SopSource(fileName, null, resource);
        }

        String label() {
            return path != null ? path.toString() : "classpath:docs/sops/" + fileName;
        }

        String readUtf8() throws IOException {
            if (path != null) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
            try (var in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
