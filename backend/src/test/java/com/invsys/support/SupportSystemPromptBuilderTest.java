package com.invsys.support;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportSystemPromptBuilderTest {

    @Test
    void includesLocalizedPagePlaybookAndLedgerSafetyRule() {
        String prompt = SupportSystemPromptBuilder.build(
                List.of("WAREHOUSE_MANAGER"),
                "/sales-orders",
                List.of(),
                Map.of(
                        "title", "Sales Orders",
                        "purpose", "Confirm and allocate demand",
                        "reversals", List.of("Un-allocate releases stock to the pool."),
                        "flow", List.of("Confirm", "Allocate"),
                        "correlations", List.of("Feeds the picking wave.")));

        assertThat(prompt).contains("Localized page playbook");
        assertThat(prompt).contains("Sales Orders");
        assertThat(prompt).contains("Un-allocate releases stock");
        assertThat(prompt).containsIgnoringCase("append-only");
        assertThat(prompt).contains("/sales-orders");
    }

    @Test
    void formatPageContextHandlesEmptyMaps() {
        assertThat(SupportSystemPromptBuilder.formatPageContext(Map.of())).isEmpty();
        assertThat(SupportSystemPromptBuilder.formatPageContext(null)).isEmpty();
    }
}
