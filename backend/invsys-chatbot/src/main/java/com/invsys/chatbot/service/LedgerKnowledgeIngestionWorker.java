package com.invsys.chatbot.service;

import com.invsys.core.tenancy.TenantContext;
import com.invsys.repository.TenantRepository;
import com.invsys.support.SupportKnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * ETL worker: mines recurring offline sync conflicts and damaged-barcode exceptions,
 * then upserts grounded {@code auto-learned-*} SOP chunks into {@code support_knowledge_chunks}.
 */
@Component
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "invsys.support.knowledge.ledger-ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class LedgerKnowledgeIngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(LedgerKnowledgeIngestionWorker.class);
    private static final int MIN_OCCURRENCES = 3;
    private static final int MAX_PATTERNS_PER_TENANT = 8;

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SupportKnowledgeRepository knowledgeRepository;
    private final EmbeddingModel embeddingModel;
    private final ExecutorService virtualThreadExecutor;

    public LedgerKnowledgeIngestionWorker(
            TenantRepository tenantRepository,
            JdbcTemplate jdbcTemplate,
            SupportKnowledgeRepository knowledgeRepository,
            EmbeddingModel embeddingModel,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor
    ) {
        this.tenantRepository = tenantRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.knowledgeRepository = knowledgeRepository;
        this.embeddingModel = embeddingModel;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    @Scheduled(cron = "${invsys.support.knowledge.ledger-ingestion.cron:0 20 3 * * *}")
    public void scheduleIngestion() {
        virtualThreadExecutor.execute(this::ingestAllTenants);
    }

    void ingestAllTenants() {
        for (UUID tenantId : tenantRepository.findAll().stream().map(t -> t.getId()).toList()) {
            try {
                int upserted = ingestTenant(tenantId);
                if (upserted > 0) {
                    log.info("Ledger knowledge ingestion upserted {} chunks for tenant {}", upserted, tenantId);
                }
            } catch (Exception ex) {
                log.warn("Ledger knowledge ingestion failed for tenant {}: {}", tenantId, ex.toString());
            }
        }
    }

    int ingestTenant(UUID tenantId) {
        Optional<UUID> previous = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            int upserted = 0;
            for (PatternRow row : loadConflictPatterns(tenantId)) {
                if (upsertPattern(tenantId, row)) {
                    upserted++;
                }
            }
            for (PatternRow row : loadDamagedBarcodePatterns(tenantId)) {
                if (upsertPattern(tenantId, row)) {
                    upserted++;
                }
            }
            return upserted;
        } finally {
            if (previous.isPresent()) {
                TenantContext.setTenantId(previous.get());
            } else {
                TenantContext.clear();
            }
        }
    }

    private List<PatternRow> loadConflictPatterns(UUID tenantId) {
        try {
            return jdbcTemplate.query("""
                    SELECT COALESCE(action_type, 'UNKNOWN') AS pattern_key,
                           COALESCE(split_part(request_url, '?', 1), '/exceptions') AS route_hint,
                           COUNT(*)::int AS occurrences,
                           MAX(LEFT(COALESCE(error_message, ''), 240)) AS sample_error
                      FROM offline_sync_conflicts
                     WHERE tenant_id = ?
                       AND (
                            status IN ('PENDING', 'RETRY_REQUESTED', 'CONFLICT')
                            OR COALESCE(action_type, '') ILIKE '%CONFLICT%'
                           )
                       AND created_at > now() - interval '30 days'
                     GROUP BY 1, 2
                    HAVING COUNT(*) >= ?
                     ORDER BY COUNT(*) DESC
                     LIMIT ?
                    """,
                    (rs, i) -> new PatternRow(
                            "conflict",
                            rs.getString("pattern_key"),
                            rs.getString("route_hint"),
                            rs.getInt("occurrences"),
                            rs.getString("sample_error")),
                    tenantId,
                    MIN_OCCURRENCES,
                    MAX_PATTERNS_PER_TENANT);
        } catch (RuntimeException ex) {
            log.debug("Conflict pattern query skipped: {}", ex.toString());
            return List.of();
        }
    }

    private List<PatternRow> loadDamagedBarcodePatterns(UUID tenantId) {
        try {
            return jdbcTemplate.query("""
                    SELECT COALESCE(entity_type, 'ALLOCATION') AS pattern_key,
                           '/fulfillment' AS route_hint,
                           COUNT(*)::int AS occurrences,
                           MAX(LEFT(COALESCE(diff::text, action), 240)) AS sample_error
                      FROM audit_log
                     WHERE tenant_id = ?
                       AND (
                            action = 'EXCEPTION_DAMAGED_BARCODE'
                            OR COALESCE(diff::text, '') ILIKE '%EXCEPTION_DAMAGED_BARCODE%'
                            OR COALESCE(diff::text, '') ILIKE '%"status":"CONFLICT"%'
                           )
                       AND created_at > now() - interval '30 days'
                     GROUP BY 1, 2
                    HAVING COUNT(*) >= ?
                     ORDER BY COUNT(*) DESC
                     LIMIT ?
                    """,
                    (rs, i) -> new PatternRow(
                            "damaged-barcode",
                            rs.getString("pattern_key"),
                            rs.getString("route_hint"),
                            rs.getInt("occurrences"),
                            rs.getString("sample_error")),
                    tenantId,
                    MIN_OCCURRENCES,
                    MAX_PATTERNS_PER_TENANT);
        } catch (RuntimeException ex) {
            log.debug("Damaged-barcode pattern query skipped: {}", ex.toString());
            return List.of();
        }
    }

    private boolean upsertPattern(UUID tenantId, PatternRow row) {
        String slug = "auto-learned-" + shortHash(tenantId + "|" + row.kind() + "|" + row.patternKey() + "|" + row.routeHint());
        String title = switch (row.kind()) {
            case "damaged-barcode" -> "Auto-learned: damaged barcode exception recovery";
            default -> "Auto-learned: offline sync conflict — " + row.patternKey();
        };
        String body = """
                **Operational Diagnosis:** Recurring operational blocker (%s) observed %d times in the last 30 days \
                for pattern %s on route %s.

                **Action Plan**
                1. Open the on-screen **Conflict Panel** or **Exceptions** view for this route.
                2. Confirm the parked scan or damaged barcode details against the physical label.
                3. Choose **Discard** to drop a bad parked mutation, or **Approve & Re-process** after fixing stock.
                4. For damaged barcodes, use **Skip & Flag** / exception clear, then print a replacement label.

                **↺ Ledger Safety & Reversal Rule**
                Resolving a conflict or exception adds a correction trail — never erase stock history.

                **👥 Downstream Impact**
                Pickers and managers see updated task state on handhelds and desktop after the conflict is cleared.

                Sample signal: %s
                """.formatted(
                row.kind(),
                row.occurrences(),
                row.patternKey(),
                row.routeHint() == null ? "/exceptions" : row.routeHint(),
                row.sampleError() == null || row.sampleError().isBlank() ? "(none)" : row.sampleError().strip()
        ).strip();

        String route = row.routeHint() == null || row.routeHint().isBlank() ? "/exceptions" : row.routeHint();
        float[] embedding = embeddingModel.embed(title + "\n" + body);
        knowledgeRepository.upsert(
                slug,
                title,
                body,
                List.of("PICKER", "WAREHOUSE_MANAGER", "OWNER"),
                List.of(route, "/exceptions", "/fulfillment"),
                "etl://ledger-knowledge-ingestion/" + row.kind(),
                embedding);
        return true;
    }

    private static String shortHash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private record PatternRow(
            String kind,
            String patternKey,
            String routeHint,
            int occurrences,
            String sampleError
    ) {
    }
}
