package com.invsys.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Logs which Support AI beans are present at boot so Gemini vs heuristic is obvious in Docker logs.
 */
@Component
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class SupportAiStartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupportAiStartupLogger.class);

    private final SupportAiProperties properties;
    private final ObjectProvider<ChatModel> chatModel;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final ObjectProvider<EmbeddingModel> embeddingModel;

    public SupportAiStartupLogger(
            SupportAiProperties properties,
            ObjectProvider<ChatModel> chatModel,
            ObjectProvider<ChatClient.Builder> chatClientBuilder,
            ObjectProvider<EmbeddingModel> embeddingModel
    ) {
        this.properties = properties;
        this.chatModel = chatModel;
        this.chatClientBuilder = chatClientBuilder;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        ChatModel model = chatModel.getIfAvailable();
        ChatClient.Builder builder = null;
        try {
            builder = chatClientBuilder.getIfAvailable();
        } catch (RuntimeException ignored) {
            // prototype builder creation can fail if ChatModel missing
        }
        EmbeddingModel embedding = embeddingModel.getIfAvailable();
        log.info(
                "Support AI ready llm={} chatModel={} chatClientBuilder={} embeddingModel={}",
                properties.getLlm(),
                model == null ? "absent" : model.getClass().getSimpleName(),
                builder != null,
                embedding == null ? "absent" : embedding.getClass().getSimpleName());
    }
}
