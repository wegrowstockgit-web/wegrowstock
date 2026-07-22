package com.invsys.chatbot.config;

import com.invsys.support.HashEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * CI / headless fallback when Google GenAI embeddings are not on the classpath as a bean
 * (no {@code GEMINI_API_KEY}, or {@code spring.ai.model.embedding.text=none}).
 * Runs late so a real provider {@link EmbeddingModel} wins when present.
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class HashEmbeddingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel hashEmbeddingModel() {
        return new HashEmbeddingModel();
    }
}
