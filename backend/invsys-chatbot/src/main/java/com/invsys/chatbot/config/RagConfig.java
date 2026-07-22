package com.invsys.chatbot.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring AI {@link PgVectorStore} mapped onto {@code support_knowledge_chunks}
 * (Flyway V089 + V092). Dimensions match {@code text-embedding-004} / HashEmbeddingModel (768).
 */
@Configuration
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class RagConfig {

    public static final int EMBEDDING_DIMENSIONS = 768;
    public static final String VECTOR_TABLE = "support_knowledge_chunks";

    @Bean
    public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(EMBEDDING_DIMENSIONS)
                .distanceType(PgDistanceType.COSINE_DISTANCE)
                .indexType(PgIndexType.HNSW)
                .schemaName("public")
                .vectorTableName(VECTOR_TABLE)
                .initializeSchema(false)
                .build();
    }
}
