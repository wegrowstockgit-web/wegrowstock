package com.invsys.support;

import com.invsys.chatbot.service.QueryRewriterService;

import com.invsys.support.dto.ActionDraft;
import com.invsys.support.tools.SupportCopilotReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceProactiveDraftTest {

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
    @Mock ObjectProvider<QueryRewriterService> queryRewriter;

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
        lenient().when(queryRewriter.getIfAvailable()).thenReturn(null);
        lenient().when(escalationContext.consumeCard()).thenReturn(java.util.Optional.empty());
        lenient().when(readService.formatLiveFactsForPrompt(anyString(), any())).thenReturn("");
        lenient().when(graphRepository.retrieveWithGraph(anyList(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of());
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
                chatMemory,
                queryRewriter);
    }

    @Test
    void salesOrdersPageStateTriggersBottleneckInsightOnChatComplete() {
        when(bottleneckService.detectProactiveInsight("/sales-orders"))
                .thenReturn("💡 3 orders are currently stuck on Credit Hold. Tap to review.");

        AtomicReference<SupportStructuredReply> reply = new AtomicReference<>();
        service.streamAnswer(
                "How do I resolve these holds?",
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                Map.of("title", "Sales Orders"),
                Map.of("pathname", "/sales-orders", "userRoles", List.of("WAREHOUSE_MANAGER")),
                token -> {},
                action -> {},
                reply::set);

        assertThat(reply.get()).isNotNull();
        assertThat(reply.get().proactiveInsight()).contains("Credit Hold");
        verify(bottleneckService).detectProactiveInsight("/sales-orders");
    }

    @Test
    void unallocateQuestionAttachesActionDraftWithPostMethod() {
        when(bottleneckService.detectProactiveInsight(anyString())).thenReturn(null);

        AtomicReference<SupportStructuredReply> reply = new AtomicReference<>();
        service.streamAnswer(
                "Please unallocate reserved stock",
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                Map.of(),
                Map.of("pathname", "/sales-orders", "selectedEntityId", "SO-99"),
                token -> {},
                action -> {},
                reply::set);

        ActionDraft draft = reply.get().actionDraft();
        assertThat(draft).isNotNull();
        assertThat(draft.httpMethod()).isEqualTo("POST");
        assertThat(draft.targetEndpoint()).contains("/sales-orders/SO-99/allocate");
        assertThat(draft.payload()).containsEntry("intent", "unallocate");
    }

    @Test
    void usesChatClientLlmAcceptsGeminiMode() {
        properties.setLlm("gemini");
        assertThat(service).isNotNull();
        // ChatClient absent → still falls back to heuristic without throwing.
        AtomicReference<SupportStructuredReply> reply = new AtomicReference<>();
        service.streamAnswer(
                "What should I do next?",
                List.of("PICKER"),
                "/fulfillment",
                Map.of(),
                Map.of("pathname", "/fulfillment"),
                token -> {},
                action -> {},
                reply::set);
        assertThat(reply.get().replyMarkdown()).isNotBlank();
    }

    @Test
    void emptyMessageReturnsStarterHint() {
        AtomicReference<SupportStructuredReply> reply = new AtomicReference<>();
        List<String> tokens = new ArrayList<>();
        service.streamAnswer(
                "   ",
                List.of("PICKER"),
                "/fulfillment",
                tokens::add,
                a -> {},
                reply::set);
        assertThat(tokens.getFirst()).containsIgnoringCase("Ask a short");
        assertThat(reply.get().replyMarkdown()).containsIgnoringCase("Ask a short");
    }
}
