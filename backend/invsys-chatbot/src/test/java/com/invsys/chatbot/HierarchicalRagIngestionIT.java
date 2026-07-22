package com.invsys.chatbot;

import com.invsys.chatbot.service.ChunkMetadataExtraction;
import com.invsys.chatbot.service.DocumentIngestionService;
import com.invsys.chatbot.service.DocumentIngestionService.IngestRequest;
import com.invsys.chatbot.service.DocumentIngestionService.IngestResult;
import com.invsys.support.HashEmbeddingModel;
import com.invsys.support.SupportKnowledgeChunk;
import com.invsys.support.SupportKnowledgeRepository;
import com.invsys.support.SupportSystemPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hierarchical RAG ingestion: parent/child links, enriched metadata, parent context assembly.
 * Uses mocked persistence so {@code mvn test -pl invsys-chatbot} stays headless-safe.
 */
@ExtendWith(MockitoExtension.class)
class HierarchicalRagIngestionIT {

    @Mock SupportKnowledgeRepository knowledgeRepository;
    @Mock ObjectProvider<ChatModel> chatModel;

    DocumentIngestionService service;
    final List<CapturedChunk> captured = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lenient().when(chatModel.getIfAvailable()).thenReturn(null);
        lenient().when(knowledgeRepository.upsertHierarchical(
                anyString(),
                anyString(),
                anyString(),
                anyList(),
                anyList(),
                anyString(),
                any(float[].class),
                ArgumentMatchers.nullable(UUID.class),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.nullable(String.class),
                anyString()
        )).thenAnswer(inv -> {
            String slug = inv.getArgument(0);
            String body = inv.getArgument(2);
            UUID parentId = inv.getArgument(7);
            String parentContent = inv.getArgument(8);
            String contextSummary = inv.getArgument(9);
            String meta = inv.getArgument(10);
            UUID id = UUID.nameUUIDFromBytes(slug.getBytes());
            captured.add(new CapturedChunk(id, slug, body, parentId, parentContent, contextSummary, meta));
            return id;
        });

        service = new DocumentIngestionService(knowledgeRepository, new HashEmbeddingModel(), chatModel);
    }

    @Test
    void hierarchicalIngestCreatesParentChildLinksAndEnrichedMetadata() {
        String sop = """
                # Fulfillment Allocation SOP

                ## Overview
                Warehouse managers allocate FEFO lots on Sales Orders before releasing a pick wave.
                Pickers never create purchase orders from the handheld scanner.

                ## Conflict Panel
                When offline sync returns a 409 conflict, open the Conflict Panel.
                Choose Discard or Approve and Re-process after fixing stock.
                Managers review parked mutations; admins may override allocation locks.

                ## Damaged Barcode
                If a barcode is torn, use Skip and Flag, then print a replacement label for the SKU or LPN.
                """.repeat(8);

        IngestResult result = service.ingest(new IngestRequest(
                "Fulfillment Allocation SOP",
                "fulfillment-allocation-sop",
                sop,
                "manuals/fulfillment.md",
                List.of("WAREHOUSE_MANAGER", "PICKER"),
                List.of("/sales-orders", "/fulfillment")));

        assertThat(result.chunkCount()).isGreaterThan(0);
        assertThat(result.contextSummary()).isNotBlank();

        List<CapturedChunk> parents = captured.stream().filter(c -> c.parentId() == null).toList();
        List<CapturedChunk> children = captured.stream().filter(c -> c.parentId() != null).toList();
        assertThat(parents).isNotEmpty();
        assertThat(children).isNotEmpty();

        CapturedChunk child = children.getFirst();
        assertThat(child.parentContent()).isNotBlank();
        assertThat(child.meta()).contains("\"chunkTier\":\"CHILD\"");
        assertThat(child.meta()).contains("\"module\":");
        assertThat(child.meta()).contains("\"resolutionLevel\":");
        assertThat(child.meta()).contains("\"errorCode\":");
        assertThat(child.contextSummary()).isEqualTo(result.contextSummary());

        verify(knowledgeRepository, atLeastOnce()).upsertHierarchical(
                anyString(),
                anyString(),
                anyString(),
                anyList(),
                anyList(),
                anyString(),
                any(float[].class),
                ArgumentMatchers.nullable(UUID.class),
                ArgumentMatchers.nullable(String.class),
                ArgumentMatchers.nullable(String.class),
                anyString());
    }

    @Test
    void searchSimilarParentAssemblyExposesFullParentInPrompt() {
        UUID parentId = UUID.randomUUID();
        String parentText = "FULL PARENT: allocate FEFO lots, then release the wave, then stage cartons.";
        SupportKnowledgeChunk childHit = new SupportKnowledgeChunk(
                UUID.randomUUID(),
                "sop-p0-c0",
                "Child hit",
                "allocate FEFO",
                List.of("WAREHOUSE_MANAGER"),
                List.of("/sales-orders"),
                "manuals/fulfillment.md",
                0.91,
                parentId,
                parentText,
                "Allocation playbook summary.",
                "{\"module\":\"FULFILLMENT\",\"chunkTier\":\"CHILD\"}");

        List<SupportKnowledgeChunk> assembled = SupportKnowledgeRepository.assembleParentContext(List.of(childHit));
        assertThat(assembled).hasSize(1);
        assertThat(assembled.getFirst().promptBody()).isEqualTo(parentText);

        String prompt = SupportSystemPromptBuilder.build(
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                assembled);
        assertThat(prompt).contains("FULL PARENT: allocate FEFO lots");
    }

    @Test
    void heuristicMetadataExtractsConflictSignals() {
        ChunkMetadataExtraction meta = DocumentIngestionService.heuristicMetadata(
                "Offline 409 conflict on sales order allocation lock",
                List.of("WAREHOUSE_MANAGER"));
        assertThat(meta.module()).isEqualTo("FULFILLMENT");
        assertThat(meta.errorCode()).isEqualTo("409_CONFLICT");
        assertThat(meta.entitiesMentioned()).contains("SALES_ORDER");
    }

    @Test
    void moduleAndResolutionHintsDeriveFromRouteAndRoles() {
        assertThat(SupportKnowledgeRepository.moduleHintForRoute("/sales-orders")).isEqualTo("FULFILLMENT");
        assertThat(SupportKnowledgeRepository.moduleHintForRoute("/purchase-orders")).isEqualTo("PURCHASING");
        assertThat(SupportKnowledgeRepository.resolutionHintForRoles(List.of("PICKER")))
                .isEqualTo("OPERATOR_SELF_SERVICE");
        assertThat(SupportKnowledgeRepository.resolutionHintForRoles(List.of("WAREHOUSE_MANAGER")))
                .isEqualTo("MANAGER_REVIEW");
    }

    private record CapturedChunk(
            UUID id,
            String slug,
            String body,
            UUID parentId,
            String parentContent,
            String contextSummary,
            String meta
    ) {
    }
}
