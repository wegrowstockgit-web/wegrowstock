package com.invsys.support;

import com.invsys.chatbot.service.QueryRewriterService;

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

import com.invsys.support.dto.ActionDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceTest {

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
    void pickerInboundOmitsDesktopPoCreation() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "picker-inbound-receive",
                        "Inbound receive on the scanner",
                        "Scan the PO barcode. Scan product barcode. Follow directed putaway. "
                                + "Office managers create PO documents on desktop; pickers never create POs.")));

        String answer = ask(List.of("PICKER"), "/fulfillment", "How do I process an inbound shipment?");

        assertThat(answer).containsIgnoringCase("scan");
        assertThat(answer).containsIgnoringCase("putaway");
        assertThat(answer.toLowerCase()).contains("never create");
        assertThat(answer.toLowerCase()).doesNotContain("click create purchase order");
    }

    @Test
    void b2bCustomerCannotSeeInternalAllocations() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "b2b-showroom-orders",
                        "B2B showroom",
                        "Track orders in the showroom order tracker. No warehouse maps.")));

        String answer = ask(List.of("B2B_CUSTOMER"), "/showroom/catalog", "How do I view my inventory allocations?");

        assertThat(answer).containsIgnoringCase("showroom");
        assertThat(answer.toLowerCase()).contains("not visible");
        assertThat(answer.toLowerCase()).doesNotContain("bin location");
        assertThat(answer.toLowerCase()).doesNotContain("warehouse map");
        assertThat(answer.toLowerCase()).doesNotContain("/api/");
        assertThat(answer.toLowerCase()).doesNotContain("inventory_ledger");
    }

    @Test
    void warehouseManagerDamagedItemMentionsExceptionAndAdjust() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "manager-damaged-exception",
                        "Damaged item",
                        "Use Skip & Flag exception, inventory adjustment, cycle count, release order locks.")));

        String answer = ask(
                List.of("WAREHOUSE_MANAGER"),
                "/exceptions",
                "What should I do if an item is damaged on the floor?");

        assertThat(answer).containsIgnoringCase("exception");
        assertThat(answer.toLowerCase()).containsAnyOf("adjust", "cycle count", "skip");
    }

    @Test
    void managerCycleCountEmitsActionButton() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt())).thenReturn(List.of());

        List<SupportActionProposal> actions = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        service.streamAnswer(
                "Please generate a cycle count for zone Aisle-4",
                List.of("WAREHOUSE_MANAGER"),
                "/cycle-counts",
                sb::append,
                actions::add,
                r -> {});

        assertThat(sb.toString()).containsIgnoringCase("cycle count");
        assertThat(actions).isNotEmpty();
        assertThat(actions.getFirst().action()).isEqualTo("generateCycleCount");
        assertThat(actions.getFirst().params()).containsEntry("zoneId", "Aisle-4");
    }

    @Test
    void graphNeighborsArePassedIntoComposer() {
        SupportKnowledgeChunk seed = chunk(
                "picker-inbound-receive",
                "Inbound",
                "Scan the PO barcode and putaway.");
        SupportKnowledgeChunk neighbor = chunk(
                "office-allocate-wave",
                "Allocate",
                "Allocate sales orders then release the wave.");
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt())).thenReturn(List.of(seed));
        when(graphRepository.retrieveWithGraph(anyList(), anyInt())).thenReturn(List.of(seed, neighbor));

        String answer = ask(
                List.of("ADMIN"),
                "/sales-orders",
                "How do I run allocation after receiving?");

        assertThat(answer.toLowerCase()).containsAnyOf("allocate", "wave", "graphrag", "receiving");
    }

    @Test
    void executeActionDelegatesToAgentTools() {
        when(agentTools.execute("generateCycleCount", java.util.Map.of("zoneId", "Z1")))
                .thenReturn(java.util.Map.of("ok", true, "cycleCountId", "abc"));

        var result = service.executeAction("generateCycleCount", java.util.Map.of("zoneId", "Z1"));
        assertThat(result).containsEntry("ok", true);
        assertThat(result).containsEntry("cycleCountId", "abc");
    }

    @Test
    void liveCqrsFactsAreInjectedIntoHeuristicAnswers() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt())).thenReturn(List.of());
        when(readService.formatLiveFactsForPrompt(anyString(), any()))
                .thenReturn("Live ATP for SKU WIDGET-A: on-hand=12, reserved=2, available-to-promise=10");

        String answer = ask(
                List.of("WAREHOUSE_MANAGER"),
                "/products",
                "What is ATP for SKU WIDGET-A?");

        assertThat(answer).contains("available-to-promise=10");
        assertThat(answer).containsIgnoringCase("WIDGET-A");
        assertThat(answer).contains("Operational Diagnosis");
    }

    @Test
    void offlineConflictMentionsParkingSpace() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "ops-offline-mutation-parking",
                        "Offline scans that need manager review",
                        "If a scan cannot finish after reconnecting, it is parked for the office and the picker keeps working.")));

        String answer = ask(
                List.of("PICKER"),
                "/fulfillment",
                "What happens when an offline scan cannot finish after reconnecting?");

        assertThat(answer).containsIgnoringCase("parked");
        assertThat(answer.toLowerCase()).doesNotContain("https://");
        assertThat(answer.toLowerCase()).doesNotContain("/api/");
    }

    @Test
    void skipAndFlagMentionsExceptionService() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "ops-skip-and-flag-exceptions",
                        "Skip & Flag",
                        "Skip & Flag releases reserved stock for that line and opens an Exceptions board item for managers.")));

        String answer = ask(
                List.of("WAREHOUSE_MANAGER"),
                "/exceptions",
                "How does Skip & Flag clear allocations without inventing stock?");

        assertThat(answer).containsIgnoringCase("Skip & Flag");
        assertThat(answer.toLowerCase()).containsAnyOf("exception", "reserved", "allocation");
        assertThat(answer).doesNotContain("FulfillmentExceptionService");
    }

    @Test
    void crossDockMentionsStagingDivert() {
        when(repository.searchHybrid(any(), any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "ops-cross-dock-intercept",
                        "Cross-dock",
                        "Receiving can divert backorder freight straight to a staging lane instead of deep storage.")));

        String answer = ask(
                List.of("PICKER"),
                "/inbound/receive",
                "Why did cross-dock send me to a staging lane on receive?");

        assertThat(answer).containsIgnoringCase("staging");
        assertThat(answer).doesNotContain("CrossDockService");
    }

    @Test
    void rewriteQueryForRetrievalSkipsHydeWhenDisabledEvenIfBeanPresent() {
        properties.setHydeEnabled(false);
        QueryRewriterService rewriter = org.mockito.Mockito.mock(QueryRewriterService.class);
        // Provider must not be consulted when the feature flag is off.
        lenient().when(queryRewriter.getIfAvailable()).thenReturn(rewriter);

        assertThat(service.rewriteQueryForRetrieval("How do I resolve these holds?"))
                .isEqualTo("How do I resolve these holds?");
        org.mockito.Mockito.verify(queryRewriter, org.mockito.Mockito.never()).getIfAvailable();
        org.mockito.Mockito.verifyNoInteractions(rewriter);
    }

    @Test
    void rewriteQueryForRetrievalUsesHydeWhenEnabled() {
        properties.setHydeEnabled(true);
        QueryRewriterService rewriter = org.mockito.Mockito.mock(QueryRewriterService.class);
        when(queryRewriter.getIfAvailable()).thenReturn(rewriter);
        when(rewriter.rewriteForRetrieval("holds?")).thenReturn("Release credit hold then unallocate.");

        assertThat(service.rewriteQueryForRetrieval("holds?"))
                .isEqualTo("Release credit hold then unallocate.");
    }

    @Test
    void detectInsightDelegatesToBottleneckService() {
        when(bottleneckService.detectProactiveInsight("/sales-orders"))
                .thenReturn("3 orders are currently stuck on Credit Hold. Tap to resolve.");
        assertThat(service.detectInsight("/sales-orders")).contains("Credit Hold");
    }

    @Test
    void executeDraftDelegatesToDraftExecutor() {
        ActionDraft draft = new ActionDraft(
                "Generate cycle count",
                "Worksheet",
                "/api/v1/cycle-counts",
                Map.of("supportAction", "generateCycleCount", "zoneId", "Aisle-4"));
        when(draftExecutor.execute(draft)).thenReturn(Map.of("ok", true));
        assertThat(service.executeDraft(draft)).containsEntry("ok", true);
        verify(draftExecutor).execute(draft);
    }

    private String ask(List<String> roles, String route, String message) {
        StringBuilder sb = new StringBuilder();
        service.streamAnswer(message, roles, route, sb::append, a -> {}, r -> {});
        return sb.toString();
    }

    private static SupportKnowledgeChunk chunk(String slug, String title, String body) {
        return new SupportKnowledgeChunk(
                UUID.randomUUID(), slug, title, body, List.of(), List.of(), "test", 0.9);
    }
}
