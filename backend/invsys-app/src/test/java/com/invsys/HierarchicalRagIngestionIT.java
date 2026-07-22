package com.invsys;

import com.invsys.chatbot.service.DocumentIngestionService;
import com.invsys.chatbot.service.DocumentIngestionService.IngestRequest;
import com.invsys.chatbot.service.DocumentIngestionService.IngestResult;
import com.invsys.support.HashEmbeddingModel;
import com.invsys.support.SupportKnowledgeChunk;
import com.invsys.support.SupportKnowledgeRepository;
import com.invsys.support.SupportSystemPromptBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end hierarchical RAG against Testcontainers Postgres + pgvector (V095).
 */
class HierarchicalRagIngestionIT extends AbstractIntegrationTest {

    @Autowired DocumentIngestionService documentIngestionService;
    @Autowired SupportKnowledgeRepository knowledgeRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void ingestMultiPageSopLinksChildrenToParentsWithEnrichedMetadata() {
        String sop = """
                # Receiving to Allocation Playbook

                ## Dock receive
                Scan the purchase order barcode, then scan each SKU. Directed putaway sends product to a BIN.
                Over-receipt beyond tolerance parks a conflict for manager review.

                ## Allocate FEFO
                On Sales Orders, allocate FEFO lots before releasing the wave.
                Credit holds block allocation until billing clears. Use Un-allocate to reverse safely.

                ## Conflict recovery
                Offline sync 409 conflicts appear in the Conflict Panel.
                Discard bad parked scans or Approve and Re-process after fixing stock for the LPN.
                """.repeat(6);

        IngestResult result = documentIngestionService.ingest(new IngestRequest(
                "Receiving to Allocation Playbook",
                "receiving-to-allocation-hier",
                sop,
                "manuals/receiving-to-allocation.md",
                List.of("WAREHOUSE_MANAGER", "PICKER"),
                List.of("/purchase-orders", "/sales-orders", "/fulfillment")));

        assertThat(result.chunkCount()).isGreaterThan(0);
        assertThat(result.contextSummary()).isNotBlank();

        List<SupportKnowledgeChunk> rows = knowledgeRepository.findBySlugPrefix(result.baseSlug());
        assertThat(rows).isNotEmpty();

        List<SupportKnowledgeChunk> children = rows.stream()
                .filter(r -> r.parentChunkId() != null)
                .toList();
        assertThat(children).isNotEmpty();
        SupportKnowledgeChunk child = children.getFirst();
        assertThat(child.parentContent()).isNotBlank();
        assertThat(child.contextSummary()).isNotBlank();
        assertThat(child.enrichedMetadataJson()).contains("chunkTier");

        Integer fkOk = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM support_knowledge_chunks c
                 JOIN support_knowledge_chunks p ON p.id = c.parent_chunk_id
                 WHERE c.slug LIKE ?
                """, Integer.class, result.baseSlug() + "%");
        assertThat(fkOk).isGreaterThan(0);

        HashEmbeddingModel embedder = new HashEmbeddingModel();
        float[] query = embedder.embed("How do I allocate FEFO on sales orders after a 409 conflict?");
        List<SupportKnowledgeChunk> hits = knowledgeRepository.searchSimilar(
                query,
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                6);
        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().promptBody().length())
                .isGreaterThanOrEqualTo(hits.getFirst().body().length());

        String prompt = SupportSystemPromptBuilder.build(
                List.of("WAREHOUSE_MANAGER"), "/sales-orders", hits);
        assertThat(prompt).containsAnyOf("allocate", "FEFO", "Conflict", "wave");
    }
}
