package com.invsys.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportChatServiceTest {

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
    void pickerInboundOmitsDesktopPoCreation() {
        when(repository.searchSimilar(any(), anyList(), anyString(), anyInt()))
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
        when(repository.searchSimilar(any(), anyList(), anyString(), anyInt()))
                .thenReturn(List.of(chunk(
                        "b2b-showroom-orders",
                        "B2B showroom",
                        "Track orders in the showroom order tracker. No warehouse maps.")));

        String answer = ask(List.of("B2B_CUSTOMER"), "/showroom/catalog", "How do I view my inventory allocations?");

        assertThat(answer).containsIgnoringCase("showroom");
        assertThat(answer.toLowerCase()).contains("not visible");
        assertThat(answer.toLowerCase()).doesNotContain("bin location");
        assertThat(answer.toLowerCase()).doesNotContain("ledger");
        assertThat(answer.toLowerCase()).doesNotContain("warehouse map");
    }

    @Test
    void warehouseManagerDamagedItemMentionsExceptionAndAdjust() {
        when(repository.searchSimilar(any(), anyList(), anyString(), anyInt()))
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
        when(repository.searchSimilar(any(), anyList(), anyString(), anyInt())).thenReturn(List.of());

        List<SupportActionProposal> actions = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        service.streamAnswer(
                "Please generate a cycle count for zone Aisle-4",
                List.of("WAREHOUSE_MANAGER"),
                "/cycle-counts",
                sb::append,
                actions::add,
                () -> {});

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
        when(repository.searchSimilar(any(), anyList(), anyString(), anyInt())).thenReturn(List.of(seed));
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

    private String ask(List<String> roles, String route, String message) {
        StringBuilder sb = new StringBuilder();
        service.streamAnswer(message, roles, route, sb::append, a -> {}, () -> {});
        return sb.toString();
    }

    private static SupportKnowledgeChunk chunk(String slug, String title, String body) {
        return new SupportKnowledgeChunk(
                UUID.randomUUID(), slug, title, body, List.of(), List.of(), "test", 0.9);
    }
}
