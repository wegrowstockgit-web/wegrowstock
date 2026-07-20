package com.invsys.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(result.actions()).isEmpty();
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
        assertThat(result.answer().toLowerCase()).doesNotContain("ledger");
        assertThat(result.answer().toLowerCase()).doesNotContain("bin location");
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

        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().getFirst().type()).isEqualTo("action_button");
        assertThat(result.actions().getFirst().action()).isEqualTo("generateCycleCount");
        assertThat(result.actions().getFirst().params()).containsEntry("zoneId", "Dock-1");
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

        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().getFirst().action()).isEqualTo("releaseWave");
        assertThat(result.actions().getFirst().params()).containsEntry("waveId", waveId);
    }

    private static SupportKnowledgeChunk chunk(String slug, String body) {
        return new SupportKnowledgeChunk(
                UUID.randomUUID(), slug, slug, body, List.of(), List.of(), "test", 0.5);
    }
}
