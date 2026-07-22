package com.invsys.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.invsys.support.tools.SupportCopilotReadService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import com.invsys.domain.User;

@ExtendWith(MockitoExtension.class)
class SupportChatServicePageContextTest {

    @Mock SupportKnowledgeRepository repository;
    @Mock SupportGraphRepository graphRepository;
    @Mock ObjectProvider<ChatClient.Builder> chatClientBuilder;
    @Mock ObjectProvider<ChatModel> chatModel;
    @Mock SupportAgentTools agentTools;
    @Mock SupportEscalationTools escalationTools;
    @Mock SupportEscalationContext escalationContext;
    @Mock ObjectProvider<org.springframework.ai.vectorstore.VectorStore> vectorStore;
    @Mock ObjectProvider<org.springframework.ai.chat.memory.ChatMemory> chatMemory;
    @Mock SupportCopilotReadService readService;
    @Mock SupportBottleneckService bottleneckService;
    @Mock SupportActionDraftExecutor draftExecutor;
    @Mock ObjectProvider<List<ToolCallback>> readToolCallbacks;

    HashEmbeddingModel embeddingModel = new HashEmbeddingModel();
    SupportAiProperties properties = new SupportAiProperties();
    SupportChatService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setLlm("heuristic");
        properties.setTopK(6);
        lenient().when(chatClientBuilder.getIfAvailable()).thenReturn(null);
        lenient().when(chatModel.getIfAvailable()).thenReturn(null);
        lenient().when(readToolCallbacks.getIfAvailable()).thenReturn(List.of());
        lenient().when(vectorStore.getIfAvailable()).thenReturn(null);
        lenient().when(chatMemory.getIfAvailable()).thenReturn(null);
        lenient().when(escalationContext.consumeCard()).thenReturn(java.util.Optional.empty());
        lenient().when(readService.formatLiveFactsForPrompt(anyString(), any())).thenReturn("");
        lenient().when(bottleneckService.detectProactiveInsight(anyString())).thenReturn(null);
        lenient().when(graphRepository.retrieveWithGraph(anyList(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new SupportChatService(
                properties,
                repository,
                graphRepository,
                embeddingModel,
                chatClientBuilder,
                chatModel,
                agentTools,
                escalationTools,
                escalationContext,
                readService,
                bottleneckService,
                draftExecutor,
                readToolCallbacks,
                vectorStore,
                chatMemory);
    }

    @Test
    void extractUserQueryTailStripsSystemContextPrefix() {
        String raw = """
                System Context: The user is currently on the Sales Orders page (/sales-orders). \
                Reversal mechanism: Un-allocate. Emphasize how to safely reverse. User Query: how do I undo?
                """;
        assertThat(SupportChatService.extractUserQueryTail(raw)).isEqualTo("how do I undo?");
    }

    @Test
    void pageContextGroundsReversalAnswer() {
        when(repository.searchSimilar(any(), anyList(), anyString(), anyInt())).thenReturn(List.of());

        String message = """
                System Context: The user is currently on the Sales Orders page. \
                Purpose: Confirm demand. \
                How to undo: Un-allocate / Cancel releases reserved stock back to available. \
                Answer only with UI button labels. \
                User Query: How do I undo an allocation?
                """;

        StringBuilder sb = new StringBuilder();
        service.streamAnswer(
                message,
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                Map.of(
                        "title", "Sales Orders",
                        "purpose", "Confirm demand",
                        "reversals", List.of("Un-allocate / Cancel releases reserved stock.")),
                sb::append,
                a -> {},
                r -> {});

        assertThat(sb.toString()).containsIgnoringCase("Un-allocate");
        assertThat(sb.toString()).doesNotContain("/api/");
        assertThat(sb.toString()).doesNotContain("inventory_ledger");
    }
}
