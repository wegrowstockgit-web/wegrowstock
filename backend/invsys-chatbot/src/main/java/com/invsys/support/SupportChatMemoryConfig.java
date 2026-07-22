package com.invsys.support;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory conversation window for {@code MessageChatMemoryAdvisor} (per JVM; keyed by sessionId).
 */
@Configuration
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class SupportChatMemoryConfig {

    @Bean
    public ChatMemory supportChatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(24)
                .build();
    }
}
