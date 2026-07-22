package com.invsys;

import com.invsys.chatbot.service.QueryRewriterService;
import com.invsys.support.HashEmbeddingModel;
import com.invsys.support.SupportChatService;
import com.invsys.support.SupportKnowledgeChunk;
import com.invsys.support.SupportKnowledgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hybrid RAG: dense cosine + English tsvector sparse search fused with RRF (k=60),
 * plus HyDE rewriter availability for {@link SupportChatService}.
 */
class HybridRagSearchIT extends AbstractIntegrationTest {

    @Autowired SupportKnowledgeRepository knowledgeRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectProvider<QueryRewriterService> queryRewriter;
    @Autowired SupportChatService supportChatService;

    @Test
    void hybridSearchFusesVectorAndTextRanksWithRrf() {
        HashEmbeddingModel embedder = new HashEmbeddingModel();
        String slugA = "hybrid-rrf-fefo-" + UUID.randomUUID().toString().substring(0, 8);
        String slugB = "hybrid-rrf-conflict-" + UUID.randomUUID().toString().substring(0, 8);

        String bodyA = "FEFO allocation reserves oldest expiry lots before wave release on Sales Orders.";
        String bodyB = "Offline sync conflict panel: Discard parked scans or Approve and Re-process after stock fix.";
        knowledgeRepository.upsert(
                slugA,
                "FEFO allocation playbook",
                bodyA,
                List.of("WAREHOUSE_MANAGER"),
                List.of("/sales-orders"),
                "test://hybrid-rrf",
                embedder.embed(bodyA));
        knowledgeRepository.upsert(
                slugB,
                "Conflict panel playbook",
                bodyB,
                List.of("WAREHOUSE_MANAGER", "PICKER"),
                List.of("/exceptions"),
                "test://hybrid-rrf",
                embedder.embed(bodyB));

        Integer tsvRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM support_knowledge_chunks WHERE slug IN (?, ?) AND body_tsv IS NOT NULL",
                Integer.class,
                slugA,
                slugB);
        assertThat(tsvRows).isEqualTo(2);

        float[] queryEmbedding = embedder.embed("How do I allocate with FEFO on sales orders?");
        List<SupportKnowledgeChunk> fused = knowledgeRepository.searchHybrid(
                queryEmbedding,
                "FEFO allocate sales orders",
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                6);

        assertThat(fused).isNotEmpty();
        assertThat(fused.stream().map(SupportKnowledgeChunk::slug)).contains(slugA);

        // Pure RRF math: chunk ranked #1 in both lists → 2/(60+1).
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        SupportKnowledgeChunk c1 = new SupportKnowledgeChunk(id1, "a", "a", "a", List.of(), List.of(), "t", 0.5);
        SupportKnowledgeChunk c2 = new SupportKnowledgeChunk(id2, "b", "b", "b", List.of(), List.of(), "t", 0.4);
        List<SupportKnowledgeChunk> rrf = SupportKnowledgeRepository.reciprocalRankFusion(
                List.of(c1, c2),
                List.of(c1),
                2);
        assertThat(rrf.getFirst().id()).isEqualTo(id1);
        assertThat(rrf.getFirst().score()).isEqualTo(1.0 / 61.0 + 1.0 / 61.0);
    }

    @Test
    void supportChatServiceAndHydeRewriterAreWired() {
        assertThat(supportChatService).isNotNull();
        QueryRewriterService rewriter = queryRewriter.getIfAvailable();
        assertThat(rewriter).isNotNull();
        // Without a live ChatModel (CI heuristic), HyDE returns the raw query unchanged.
        assertThat(rewriter.rewriteForRetrieval("how do I resolve a conflict?")).isEqualTo("how do I resolve a conflict?");
        assertThat(supportChatService.rewriteQueryForRetrieval("allocate FEFO")).isNotBlank();
    }
}
