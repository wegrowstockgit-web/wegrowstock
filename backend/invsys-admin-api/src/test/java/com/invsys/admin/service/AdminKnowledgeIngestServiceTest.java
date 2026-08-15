package com.invsys.admin.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AdminKnowledgeIngestServiceTest {

    @Test
    void rejectsEmptyAndNonMarkdown() {
        AdminKnowledgeIngestService svc = new AdminKnowledgeIngestService(mock(DataSource.class));
        assertThrows(Exception.class, () -> svc.ingest(null));
        assertThrows(Exception.class, () -> svc.ingest(new MockMultipartFile("file", new byte[0])));
        assertThrows(Exception.class, () -> svc.ingest(
                new MockMultipartFile("file", "notes.txt", "text/plain", "hi".getBytes())));
    }

    @Test
    void hashEmbeddingIs768Dims() throws Exception {
        var method = AdminKnowledgeIngestService.class.getDeclaredMethod("hashEmbedding", String.class);
        method.setAccessible(true);
        float[] emb = (float[]) method.invoke(null, "hello world");
        assertEquals(AdminKnowledgeIngestService.EMBEDDING_DIMS, emb.length);
    }
}
