package com.invsys.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * HyDE (Hypothetical Document Embeddings): rewrite a raw user question into a short
 * hypothetical ideal SOP answer before dense retrieval.
 * <p>
 * Disabled by default ({@code invsys.support.ai.hyde.enabled=false}) — each rewrite is an
 * extra Gemini round-trip that burns free-tier quota before the main answer call.
 */
@Service
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "invsys.support.ai.hyde-enabled", havingValue = "true")
public class QueryRewriterService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriterService.class);

    private final ObjectProvider<ChatModel> chatModel;

    public QueryRewriterService(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Returns a 1–2 sentence hypothetical SOP answer for embedding, or the original query
     * when no ChatModel is available / the rewrite fails.
     */
    public String rewriteForRetrieval(String rawQuery) {
        String question = rawQuery == null ? "" : rawQuery.trim();
        if (question.isBlank()) {
            return question;
        }
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return question;
        }
        try {
            String hypothetical = ChatClient.create(model)
                    .prompt()
                    .system("""
                            You rewrite warehouse support questions into a short hypothetical SOP answer \
                            that would appear in an operations manual. Reply with exactly 2 sentences. \
                            No markdown headings. No tools. No speculation about tenant data.
                            """)
                    .user("Question: " + question)
                    .call()
                    .content();
            if (hypothetical == null || hypothetical.isBlank()) {
                return question;
            }
            String cleaned = hypothetical.trim();
            log.debug("HyDE rewrite ok chars={}→{}", question.length(), cleaned.length());
            return cleaned;
        } catch (RuntimeException ex) {
            log.warn("HyDE rewrite failed; using raw query: {}", ex.toString());
            return question;
        }
    }
}
