package com.invsys.support;

import com.invsys.chatbot.service.QueryRewriterService;

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

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceMultimodalTest {

    private static final String TINY_JPEG_B64 = Base64.getEncoder().encodeToString(new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9
    });

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
        lenient().when(bottleneckService.detectProactiveInsight(anyString())).thenReturn(null);
        lenient().when(graphRepository.retrieveWithGraph(anyList(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "damage-label",
                        "Damaged label",
                        "When a barcode is torn, open Inventory Lots and print a replacement label.")));
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
    void attachedPhotoPrependsVisionCoachGuidance() {
        AtomicReference<SupportStructuredReply> reply = new AtomicReference<>();
        service.streamAnswer(
                "This barcode is torn — what should I do?",
                List.of("PICKER"),
                "/inbound/receive",
                Map.of(),
                Map.of("pathname", "/inbound/receive"),
                TINY_JPEG_B64,
                "image/jpeg",
                token -> {},
                action -> {},
                reply::set);

        assertThat(reply.get().replyMarkdown()).containsIgnoringCase("photo was attached");
        assertThat(reply.get().replyMarkdown()).containsIgnoringCase("torn labels");
        assertThat(reply.get().replyMarkdown()).doesNotContain("SupportChatService");
    }

    @Test
    void imageOnlyMessageUsesPhotoInspectionDefaultQuestion() {
        AtomicReference<SupportStructuredReply> reply = new AtomicReference<>();
        service.streamAnswer(
                "   ",
                List.of("PICKER"),
                "/inbound/receive",
                Map.of(),
                Map.of(),
                TINY_JPEG_B64,
                "image/jpeg",
                token -> {},
                action -> {},
                reply::set);

        assertThat(reply.get().replyMarkdown()).isNotBlank();
        assertThat(reply.get().replyMarkdown()).containsIgnoringCase("photo was attached");
    }

    @Test
    void dataUriImagePayloadIsAccepted() {
        String dataUri = "data:image/jpeg;base64," + TINY_JPEG_B64;
        AtomicReference<SupportStructuredReply> reply = new AtomicReference<>();
        service.streamAnswer(
                "Can you read the SKU from this label?",
                List.of("WAREHOUSE_MANAGER"),
                "/products",
                Map.of(),
                Map.of("pathname", "/products"),
                dataUri,
                "image/jpeg",
                token -> {},
                action -> {},
                reply::set);

        assertThat(reply.get().replyMarkdown()).containsIgnoringCase("photo was attached");
    }

    private static SupportKnowledgeChunk chunk(String slug, String title, String body) {
        return new SupportKnowledgeChunk(
                UUID.randomUUID(), slug, title, body, List.of(), List.of(), "test", 0.9);
    }
}
