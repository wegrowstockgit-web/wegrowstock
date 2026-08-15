package com.invsys.admin.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminKnowledgeChunkUtilTest {

    @Test
    void chunkAndVectorHelpers() {
        var chunks = AdminKnowledgeIngestService.chunkText("abcdefghij", 3);
        assertEquals(4, chunks.size());
        assertTrue(AdminKnowledgeIngestService.chunkText("  ", 10).isEmpty());

        float[] emb = AdminKnowledgeIngestService.hashEmbedding("coverage");
        String lit = AdminKnowledgeIngestService.toVectorLiteral(emb);
        assertTrue(lit.startsWith("["));
        assertTrue(lit.endsWith("]"));
        assertEquals(AdminKnowledgeIngestService.EMBEDDING_DIMS, emb.length);
    }
}
