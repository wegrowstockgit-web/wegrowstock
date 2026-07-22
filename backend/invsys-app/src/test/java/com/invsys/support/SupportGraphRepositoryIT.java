package com.invsys.support;

import com.invsys.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphRAG 2-hop expansion against seeded nodes/edges (Testcontainers + pgvector).
 */
class SupportGraphRepositoryIT extends AbstractIntegrationTest {

    @Autowired SupportGraphRepository graphRepository;
    @Autowired SupportKnowledgeRepository knowledgeRepository;

    @Test
    void retrieveWithGraphPullsTwoHopNeighbors() {
        assertThat(graphRepository.nodeCount()).isGreaterThan(0);
        assertThat(knowledgeRepository.count()).isGreaterThan(0);

        SupportKnowledgeChunk seed = new SupportKnowledgeChunk(
                UUID.randomUUID(),
                "picker-inbound-receive",
                "Inbound receive",
                "Scan PO and putaway",
                List.of("PICKER"),
                List.of("/fulfillment"),
                "seed",
                0.99);

        List<SupportKnowledgeChunk> merged = graphRepository.retrieveWithGraph(List.of(seed), 2);
        List<String> slugs = merged.stream().map(SupportKnowledgeChunk::slug).toList();

        assertThat(slugs).contains("picker-inbound-receive");
        // 1-hop UNLOCKS → allocate; 2-hop DEPENDS_ON_STOCK_FROM → create PO
        assertThat(slugs).contains("office-allocate-wave");
        assertThat(slugs).contains("office-create-po");
        // Seed stays first in merge order
        assertThat(slugs.getFirst()).isEqualTo("picker-inbound-receive");
    }

    @Test
    void expandChunkSlugsExcludesSeeds() {
        List<String> neighbors = graphRepository.expandChunkSlugs(List.of("office-allocate-wave"), 2);
        assertThat(neighbors).doesNotContain("office-allocate-wave");
        assertThat(neighbors).contains("office-create-po");
    }

    @Test
    void emptySeedsReturnEmptyExpansion() {
        assertThat(graphRepository.expandChunkSlugs(List.of(), 2)).isEmpty();
        assertThat(graphRepository.retrieveWithGraph(List.of(), 2)).isEmpty();
    }
}
