package com.invsys.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationsInstructorFormatterTest {

    @Test
    void wrapsAnswerWithInstructorSectionsAndChips() {
        HeuristicSupportResult raw = HeuristicSupportResult.of(
                "Allocation reserves FEFO lots for the order.");
        HeuristicSupportResult enriched = OperationsInstructorFormatter.enrich(
                raw,
                "Why is allocation stuck on BACKORDERED?",
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                Map.of("selectedEntity", "SO-1"));

        assertThat(enriched.answer()).contains("**Diagnosis:**");
        assertThat(enriched.answer()).contains("**Action plan**");
        assertThat(enriched.answer()).contains("↺ Reversal Guide");
        assertThat(enriched.answer()).contains("👥 Downstream Impact");
        assertThat(enriched.actions()).anyMatch(a -> "NAVIGATE".equals(a.action()));
        assertThat(enriched.actions()).anyMatch(a ->
                "SPOTLIGHT".equals(a.action()) && a.target().contains("btn-unallocate"));
        assertThat(enriched.followUps()).isNotEmpty();
    }

    @Test
    void shieldsPickerFromDesktopAdminNavigation() {
        HeuristicSupportResult raw = HeuristicSupportResult.of("Scan the PO then putaway to the directed bin.");
        HeuristicSupportResult enriched = OperationsInstructorFormatter.enrich(
                raw,
                "How do I process inbound?",
                List.of("PICKER"),
                "/fulfillment",
                Map.of());

        assertThat(enriched.actions()).allMatch(a ->
                !"NAVIGATE".equals(a.action()) || a.target().contains("fulfillment")
                        || a.target().startsWith("/inbound"));
        assertThat(enriched.actions()).noneMatch(a ->
                "NAVIGATE".equals(a.action()) && a.target().contains("settings"));
        assertThat(enriched.answer()).containsIgnoringCase("Skip & Flag");
    }
}
