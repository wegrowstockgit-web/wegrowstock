package com.invsys.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.invsys.domain.User;

class HeuristicSupportComposerTest {

    @Test
    void pickerInboundUsesScannerDoc() {
        var result = HeuristicSupportComposer.compose(
                "How do I process an inbound shipment?",
                List.of("PICKER"),
                "/fulfillment",
                List.of(chunk("picker-inbound-receive", "Scan PO. Follow directed putaway.")),
                "system");

        assertThat(result.answer()).containsIgnoringCase("putaway");
        assertThat(result.answer()).containsIgnoringCase("scan");
        assertThat(result.actions()).anyMatch(a -> "NAVIGATE".equals(a.action()));
        assertThat(result.followUps()).isNotEmpty();
    }

    @Test
    void b2bAllocationStaysInShowroomSandbox() {
        var result = HeuristicSupportComposer.compose(
                "How do I view my inventory allocations?",
                List.of("B2B_CUSTOMER"),
                "/showroom/catalog",
                List.of(chunk("internal-bins", "Aisle A bin locations and ledger rows.")),
                "system");

        assertThat(result.answer()).containsIgnoringCase("showroom");
        assertThat(result.answer()).containsIgnoringCase("not visible");
        assertThat(result.answer().toLowerCase()).doesNotContain("bin location");
        assertThat(result.answer().toLowerCase()).doesNotContain("inventory_ledger");
        assertThat(result.actions()).anyMatch(a -> "NAVIGATE".equals(a.action()));
    }

    @Test
    void managerDamageUsesExceptionDoc() {
        var result = HeuristicSupportComposer.compose(
                "What should I do if an item is damaged on the floor?",
                List.of("WAREHOUSE_MANAGER"),
                "/exceptions",
                List.of(chunk("manager-damaged-exception", "Skip & Flag exception, adjust, cycle count.")),
                "system");

        assertThat(result.answer()).containsIgnoringCase("exception");
        assertThat(result.answer().toLowerCase()).containsAnyOf("adjust", "cycle");
    }

    @Test
    void managerCycleCountProposesActionButton() {
        var result = HeuristicSupportComposer.compose(
                "Generate cycle count for zone Dock-1",
                List.of("WAREHOUSE_MANAGER"),
                "/cycle-counts",
                List.of(),
                "system");

        assertThat(result.actions()).anyMatch(a ->
                "action_button".equals(a.type()) && "generateCycleCount".equals(a.action()));
        assertThat(result.actions()).anyMatch(a ->
                "generateCycleCount".equals(a.action()) && "Dock-1".equals(a.params().get("zoneId")));
        assertThat(result.answer()).contains("**Operational Diagnosis:**");
        assertThat(result.followUps()).isNotEmpty();
    }

    @Test
    void emptyRetrievalReturnsFallbackHint() {
        var result = HeuristicSupportComposer.compose(
                "unrelated question about weather",
                List.of("ADMIN"),
                "/dashboard",
                List.of(),
                "system");

        assertThat(result.answer()).containsIgnoringCase("grounded");
        assertThat(result.answer()).contains("/dashboard");
    }

    @Test
    void allocationQuestionSurfacesGraphExpandedInboundContext() {
        var result = HeuristicSupportComposer.compose(
                "How do I allocate after receiving?",
                List.of("ADMIN"),
                "/sales-orders",
                List.of(
                        chunk("office-allocate-wave", "Allocate then release the wave for pickers."),
                        chunk("picker-inbound-receive", "Scan PO and follow directed putaway.")),
                "system");

        assertThat(result.answer()).containsIgnoringCase("allocate");
        assertThat(result.answer()).containsIgnoringCase("GraphRAG");
        assertThat(result.answer()).containsIgnoringCase("putaway");
    }

    @Test
    void managerReleaseWaveProposesActionWhenWaveIdPresent() {
        String waveId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        var result = HeuristicSupportComposer.compose(
                "Please release wave " + waveId,
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                List.of(chunk("office-allocate-wave", "Release the wave after allocation.")),
                "system");

        assertThat(result.actions()).anyMatch(a ->
                "releaseWave".equals(a.action()) && waveId.equals(a.params().get("waveId")));
        assertThat(result.actions()).anyMatch(a -> "NAVIGATE".equals(a.action()));
    }

    @Test
    void offlineConflictUsesParkingPlaybook() {
        var result = HeuristicSupportComposer.compose(
                "What happens when an offline scan cannot finish after reconnecting?",
                List.of("PICKER"),
                "/fulfillment",
                List.of(chunk(
                        "ops-offline-mutation-parking",
                        "Conflicts park for the office; the picker keeps working.")),
                "system");

        assertThat(result.answer()).containsIgnoringCase("park");
        assertThat(result.answer()).doesNotContain("OfflineSyncConflictService");
        assertThat(result.answer()).doesNotContain("HTTP");
    }

    @Test
    void skipAndFlagUsesExceptionPlaybook() {
        var result = HeuristicSupportComposer.compose(
                "How does Skip & Flag free allocations without inventing stock?",
                List.of("PICKER"),
                "/fulfillment",
                List.of(chunk(
                        "ops-skip-and-flag-exceptions",
                        "Skip & Flag releases reserved stock and opens an Exceptions board item.")),
                "system");

        assertThat(result.answer()).containsIgnoringCase("Skip & Flag");
        assertThat(result.answer()).containsIgnoringCase("exception");
        assertThat(result.answer()).doesNotContain("FulfillmentExceptionService");
    }

    @Test
    void systemContextReversalAnswersWithoutRetrievedChunks() {
        var result = HeuristicSupportComposer.compose(
                """
                System Context: The user is currently on the Sales Orders page. \
                How to undo: Un-allocate / Cancel releases reserved stock back to available. \
                Answer only with UI button labels. User Query: How do I undo allocation?
                """,
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                List.of(),
                "system");

        assertThat(result.answer()).containsIgnoringCase("Un-allocate");
        assertThat(result.answer()).containsIgnoringCase("stock");
        assertThat(result.answer()).doesNotContain("ERROR_CORRECTION");
    }

    @Test
    void crossDockUsesInterceptPlaybook() {
        var result = HeuristicSupportComposer.compose(
                "Why did receive send me to a staging lane for a backorder?",
                List.of("WAREHOUSE_MANAGER"),
                "/inbound/receive",
                List.of(chunk(
                        "ops-cross-dock-intercept",
                        "Receiving can divert freight to staging instead of deep storage.")),
                "system");

        assertThat(result.answer()).containsIgnoringCase("staging");
        assertThat(result.answer()).doesNotContain("CrossDockService");
    }

    private static SupportKnowledgeChunk chunk(String slug, String body) {
        return new SupportKnowledgeChunk(
                UUID.randomUUID(), slug, slug, body, List.of(), List.of(), "test", 0.5);
    }
}
