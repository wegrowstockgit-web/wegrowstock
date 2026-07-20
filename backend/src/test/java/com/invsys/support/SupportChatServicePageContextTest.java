package com.invsys.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
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

@ExtendWith(MockitoExtension.class)
class SupportChatServicePageContextTest {

    @Mock SupportKnowledgeRepository repository;
    @Mock SupportGraphRepository graphRepository;
    @Mock ObjectProvider<ChatClient.Builder> chatClientBuilder;
    @Mock SupportAgentTools agentTools;

    HashEmbeddingModel embeddingModel = new HashEmbeddingModel();
    SupportAiProperties properties = new SupportAiProperties();
    SupportChatService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setLlm("heuristic");
        properties.setTopK(6);
        lenient().when(chatClientBuilder.getIfAvailable()).thenReturn(null);
        lenient().when(graphRepository.retrieveWithGraph(anyList(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        service = new SupportChatService(
                properties, repository, graphRepository, embeddingModel, chatClientBuilder, agentTools);
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
                System Context: The user is currently on the Sales Orders page (/sales-orders). \
                Purpose: Confirm demand. \
                Reversal mechanism: Un-allocate / Cancel releases ACTIVE allocations back to the ATP pool. \
                Emphasize how to safely reverse or undo transactions without corrupting the append-only inventory ledger. \
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
                        "reversals", List.of("Un-allocate / Cancel releases ACTIVE allocations.")),
                sb::append,
                a -> {},
                r -> {});

        assertThat(sb.toString()).containsIgnoringCase("Un-allocate");
        assertThat(sb.toString()).containsIgnoringCase("ledger");
    }
}
