package com.invsys.support;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Agentic GraphRAG orchestrator: embed → vector seed → 2-hop graph → system prompt → LLM/tools or heuristic.
 */
@Service
public class SupportChatService {

    private final SupportAiProperties properties;
    private final SupportKnowledgeRepository repository;
    private final SupportGraphRepository graphRepository;
    private final EmbeddingModel embeddingModel;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final SupportAgentTools agentTools;

    public SupportChatService(
            SupportAiProperties properties,
            SupportKnowledgeRepository repository,
            SupportGraphRepository graphRepository,
            EmbeddingModel embeddingModel,
            ObjectProvider<ChatClient.Builder> chatClientBuilder,
            SupportAgentTools agentTools
    ) {
        this.properties = properties;
        this.repository = repository;
        this.graphRepository = graphRepository;
        this.embeddingModel = embeddingModel;
        this.chatClientBuilder = chatClientBuilder;
        this.agentTools = agentTools;
    }

    public void streamAnswer(
            String message,
            List<String> roles,
            String route,
            Consumer<String> onToken,
            Consumer<SupportActionProposal> onAction,
            Runnable onComplete
    ) {
        if (!properties.isEnabled()) {
            onToken.accept("Support assistant is disabled.");
            onComplete.run();
            return;
        }
        String question = message == null ? "" : message.trim();
        if (question.isEmpty()) {
            onToken.accept("Ask a short operations question to get started.");
            onComplete.run();
            return;
        }

        List<String> normalizedRoles = normalizeRoles(roles);
        float[] embedding = embeddingModel.embed(question);
        List<SupportKnowledgeChunk> seeds = repository.searchSimilar(
                embedding, normalizedRoles, route, properties.getTopK());
        List<SupportKnowledgeChunk> retrieved = graphRepository.retrieveWithGraph(seeds, 2);
        String system = SupportSystemPromptBuilder.build(normalizedRoles, route, retrieved)
                + """

                GraphRAG: retrieved fragments include 2-hop neighbors (e.g. Allocation pulls Purchase Orders \
                and Shipping Staging). When a mutating platform step is needed, propose a confirmable \
                action_button rather than claiming it already ran.
                """;

        if ("openai".equalsIgnoreCase(properties.getLlm()) && chatClientBuilder.getIfAvailable() != null) {
            ChatClient client = chatClientBuilder.getObject()
                    .defaultSystem(system)
                    .build();
            String content = client.prompt()
                    .user(question)
                    .tools(agentTools)
                    .call()
                    .content();
            streamChunks(content == null ? "" : content, onToken);
            // Also surface heuristic proposals so UI buttons work even if the model only narrates.
            HeuristicSupportResult side = HeuristicSupportComposer.compose(
                    question, normalizedRoles, route, retrieved, system);
            for (SupportActionProposal action : side.actions()) {
                onAction.accept(action);
            }
            onComplete.run();
            return;
        }

        HeuristicSupportResult result = HeuristicSupportComposer.compose(
                question, normalizedRoles, route, retrieved, system);
        streamChunks(result.answer(), onToken);
        for (SupportActionProposal action : result.actions()) {
            onAction.accept(action);
        }
        onComplete.run();
    }

    public Map<String, Object> executeAction(String action, Map<String, String> params) {
        return agentTools.execute(action, params);
    }

    public static List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            out.add(role.trim().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(out);
    }

    public static List<String> parseRolesHeader(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }
        return normalizeRoles(Arrays.asList(header.split("[,\\s]+")));
    }

    private static void streamChunks(String content, Consumer<String> onToken) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String[] parts = content.split("(?<=\\s)");
        for (String part : parts) {
            if (!part.isEmpty()) {
                onToken.accept(part);
            }
        }
    }
}
