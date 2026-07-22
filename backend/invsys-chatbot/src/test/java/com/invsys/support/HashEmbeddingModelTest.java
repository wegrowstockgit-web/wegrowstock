package com.invsys.support;

import com.invsys.chatbot.config.RagConfig;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HashEmbeddingModelTest {

    @Test
    void dimensionsMatchPgVectorStoreConfig() {
        EmbeddingModel model = new HashEmbeddingModel();
        assertThat(model.dimensions()).isEqualTo(RagConfig.EMBEDDING_DIMENSIONS);
        assertThat(model.dimensions()).isEqualTo(768);
        float[] vector = model.embed(new Document("inbound receive putaway"));
        assertThat(vector).hasSize(768);
    }
}
