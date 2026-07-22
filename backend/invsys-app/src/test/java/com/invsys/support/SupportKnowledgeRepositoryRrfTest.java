package com.invsys.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SupportKnowledgeRepositoryRrfTest {

    @Test
    void reciprocalRankFusionPrefersItemsRankedHighInBothLists() {
        UUID both = UUID.randomUUID();
        UUID denseOnly = UUID.randomUUID();
        UUID sparseOnly = UUID.randomUUID();

        SupportKnowledgeChunk a = chunk(both, "both");
        SupportKnowledgeChunk b = chunk(denseOnly, "dense");
        SupportKnowledgeChunk c = chunk(sparseOnly, "sparse");

        List<SupportKnowledgeChunk> fused = SupportKnowledgeRepository.reciprocalRankFusion(
                List.of(a, b),
                List.of(a, c),
                3);

        assertThat(fused).hasSize(3);
        assertThat(fused.getFirst().id()).isEqualTo(both);
        assertThat(fused.getFirst().score())
                .isEqualTo(1.0 / (SupportKnowledgeRepository.RRF_K + 1)
                        + 1.0 / (SupportKnowledgeRepository.RRF_K + 1));
    }

    @Test
    void assembleParentContextDedupesByParentId() {
        UUID parent = UUID.randomUUID();
        SupportKnowledgeChunk c1 = new SupportKnowledgeChunk(
                UUID.randomUUID(), "a", "a", "child-a", List.of(), List.of(), "t", 0.9,
                parent, "PARENT BODY", "sum", "{}");
        SupportKnowledgeChunk c2 = new SupportKnowledgeChunk(
                UUID.randomUUID(), "b", "b", "child-b", List.of(), List.of(), "t", 0.8,
                parent, "PARENT BODY", "sum", "{}");
        List<SupportKnowledgeChunk> assembled = SupportKnowledgeRepository.assembleParentContext(List.of(c1, c2));
        assertThat(assembled).hasSize(1);
        assertThat(assembled.getFirst().promptBody()).isEqualTo("PARENT BODY");
    }

    private static SupportKnowledgeChunk chunk(UUID id, String slug) {
        return new SupportKnowledgeChunk(id, slug, slug, slug, List.of(), List.of(), "t", 0.1);
    }
}
